package dev.dsf.fhir.validator.support;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.utilities.validation.ValidationMessage;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;

public class CodeValidatorForExpandedValueSets implements IValidationSupport
{
	private static record Concept(String system, String version, String code, String display)
	{
	}

	private final FhirContext fhirContext;

	public CodeValidatorForExpandedValueSets(FhirContext fhirContext)
	{
		this.fhirContext = fhirContext;
	}

	@Override
	public FhirContext getFhirContext()
	{
		return fhirContext;
	}

	@Override
	public boolean isValueSetSupported(ValidationSupportContext supportContext, String valueSetUrl)
	{
		return supportContext.getRootValidationSupport().fetchResource(ValueSet.class, valueSetUrl).hasExpansion();
	}

	@Override
	public CodeValidationResult validateCodeInValueSet(ValidationSupportContext supportContext,
			ConceptValidationOptions options, String codeSystem, String code, String display, IBaseResource valueSet)
	{
		if (valueSet == null || !(valueSet instanceof ValueSet) || !((ValueSet) valueSet).hasExpansion())
			return new CodeValidationResult().setSeverity(IssueSeverity.ERROR).setMessage("ValueSet not supported");

		ValueSetExpansionComponent expansion = ((ValueSet) valueSet).getExpansion();

		return doValidateCodeInValueSet(supportContext, options, codeSystem, code, display, expansion);
	}

	public CodeValidationResult doValidateCodeInValueSet(ValidationSupportContext supportContext,
			ConceptValidationOptions options, String targetCodeSystem, String targetCode, String targetDisplay,
			ValueSetExpansionComponent expansion)
	{
		String targetCodeSystemVersion = null;

		String[] targetCodeSystemSplit = targetCodeSystem.split("\\|");
		if (targetCodeSystemSplit.length == 2)
		{
			targetCodeSystem = targetCodeSystemSplit[0];
			targetCodeSystemVersion = targetCodeSystemSplit[1];
		}

		boolean codeSystemCaseSensitive = true;
		CodeSystem codeSystem = null;

		if (!options.isInferSystem() && isNotBlank(targetCodeSystem))
			codeSystem = (CodeSystem) supportContext.getRootValidationSupport().fetchCodeSystem(
					targetCodeSystemVersion != null ? (targetCodeSystem + "|" + targetCodeSystemVersion)
							: targetCodeSystem);

		List<Concept> codes = new ArrayList<>();
		flatten(expansion.getContains(), codes);

		String codeSystemName = null;
		String codeSystemVersion = null;
		String codeSystemContentMode = null;

		if (codeSystem != null)
		{
			codeSystemCaseSensitive = codeSystem.getCaseSensitive();
			codeSystemName = codeSystem.getName();
			codeSystemVersion = codeSystem.getVersion();
			codeSystemContentMode = codeSystem.getContentElement().getValueAsString();
		}
		else
			codeSystemVersion = targetCodeSystemVersion;

		for (Concept nextExpansionCode : codes)
		{
			boolean codeMatches;
			if (codeSystemCaseSensitive)
				codeMatches = defaultString(targetCode).equals(nextExpansionCode.code());
			else
				codeMatches = defaultString(targetCode).equalsIgnoreCase(nextExpansionCode.code());

			if (codeMatches)
			{
				if (options.isInferSystem()
						|| (nextExpansionCode.system().equals(targetCodeSystem) && (targetCodeSystemVersion == null
								|| targetCodeSystemVersion.equals(nextExpansionCode.version()))))
				{
					if (!options.isValidateDisplay() || (isBlank(nextExpansionCode.display()) || isBlank(targetDisplay)
							|| nextExpansionCode.display().equals(targetDisplay)))
					{
						return new CodeValidationResult().setCode(targetCode).setDisplay(nextExpansionCode.display())
								.setCodeSystemName(codeSystemName).setCodeSystemVersion(codeSystemVersion);
					}
					else
					{
						return new CodeValidationResult().setSeverity(IssueSeverity.ERROR)
								.setDisplay(nextExpansionCode.display())
								.setMessage("Concept Display \"" + targetDisplay + "\" does not match expected \""
										+ nextExpansionCode.display() + "\"")
								.setCodeSystemName(codeSystemName).setCodeSystemVersion(codeSystemVersion);
					}
				}
			}
		}

		ValidationMessage.IssueSeverity severity;
		String message;
		if ("fragment".equals(codeSystemContentMode))
		{
			severity = ValidationMessage.IssueSeverity.WARNING;
			message = "Unknown code in fragment CodeSystem '"
					+ (isNotBlank(targetCodeSystem) ? targetCodeSystem + "#" : "") + targetCode + "'";
		}
		else
		{
			severity = ValidationMessage.IssueSeverity.ERROR;
			message = "Unknown code '" + (isNotBlank(targetCodeSystem) ? targetCodeSystem + "#" : "") + targetCode
					+ "'";
		}

		return new CodeValidationResult().setSeverity(IssueSeverity.fromCode(severity.toCode())).setMessage(message);
	}

	private void flatten(List<ValueSetExpansionContainsComponent> components, List<Concept> concepts)
	{
		for (ValueSetExpansionContainsComponent next : components)
		{
			concepts.add(new Concept(next.getSystem(), next.getVersion(), next.getCode(), next.getDisplay()));

			flatten(next.getContains(), concepts);
		}
	}
}
