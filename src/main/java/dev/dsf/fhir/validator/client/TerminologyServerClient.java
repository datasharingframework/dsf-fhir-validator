package dev.dsf.fhir.validator.client;

import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ValueSet;

import jakarta.ws.rs.WebApplicationException;

public interface TerminologyServerClient
{
	record ValidationResult(boolean result, String message, OperationOutcome outome)
	{
	}

	record UrlAndVersion(String url, String version)
	{
		public static UrlAndVersion fromCodeSystem(CodeSystem cs)
		{
			return new UrlAndVersion(cs.getUrl(), cs.getVersion());
		}
	}

	/**
	 * @param valueSet
	 *            not <code>null</code>
	 * @return expanded {@link ValueSet}, never <code>null</code>
	 * @throws WebApplicationException
	 */
	ValueSet expand(ValueSet valueSet) throws WebApplicationException;

	CapabilityStatement getMetadata() throws WebApplicationException;

	/**
	 * @param coding
	 *            not <code>null</code>
	 * @return
	 * @throws WebApplicationException
	 */
	ValidationResult validate(Coding coding) throws WebApplicationException;

	/**
	 * @param system
	 *            not <code>null</code>
	 * @param code
	 *            not <code>null</code>
	 * @param display
	 *            may be <code>null</code>
	 * @param version
	 *            may be <code>null</code>
	 * @return
	 * @throws WebApplicationException
	 */
	default ValidationResult validate(String system, String code, String display, String version)
			throws WebApplicationException
	{
		Objects.requireNonNull(system, "system");
		Objects.requireNonNull(code, "code");
		// display may be null
		// version may be null

		return validate(new Coding(system, code, display).setVersion(version));
	}

	/**
	 * @param url
	 *            not <code>null</code>
	 * @return
	 * @throws WebApplicationException
	 */
	List<UrlAndVersion> getSupportedCodeSystemVersion(String url) throws WebApplicationException;
}
