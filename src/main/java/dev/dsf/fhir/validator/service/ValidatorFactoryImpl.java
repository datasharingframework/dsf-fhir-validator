package dev.dsf.fhir.validator.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Enumerations.BindingStrength;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import dev.dsf.fhir.validator.client.TerminologyServerClient;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageWithDepedencies;
import dev.dsf.fhir.validator.structure_definition.SnapshotGenerator;
import dev.dsf.fhir.validator.structure_definition.SnapshotGenerator.SnapshotWithValidationMessages;
import dev.dsf.fhir.validator.support.CodeValidatorForExpandedValueSets;
import dev.dsf.fhir.validator.support.NonValidatingValidationSupport;
import dev.dsf.fhir.validator.support.QuietCommonCodeSystemsTerminologyService;
import dev.dsf.fhir.validator.support.ValidationSupportWithCustomResources;
import dev.dsf.fhir.validator.value_set.ValueSetExpander;
import jakarta.ws.rs.WebApplicationException;

public class ValidatorFactoryImpl implements ValidatorFactory, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValidatorFactoryImpl.class);

	public static final EnumSet<BindingStrength> DEFAULT_VALUE_SET_BINDING_STRENGTHS = EnumSet
			.allOf(BindingStrength.class);

	private final FhirContext fhirContext;
	private final BiFunction<FhirContext, IValidationSupport, SnapshotGenerator> internalSnapshotGeneratorFactory;
	private final BiFunction<FhirContext, IValidationSupport, ValueSetExpander> internalValueSetExpanderFactory;
	private final TerminologyServerClient terminologyServerClient;
	private final EnumSet<BindingStrength> valueSetBindingStrengths;

	/**
	 * @param fhirContext
	 *            not <code>null</code>
	 * @param internalSnapshotGeneratorFactory
	 *            not <code>null</code>
	 * @param internalValueSetExpanderFactory
	 *            not <code>null</code>
	 * @param terminologyServerClient
	 *            not <code>null</code>
	 */
	public ValidatorFactoryImpl(FhirContext fhirContext,
			BiFunction<FhirContext, IValidationSupport, SnapshotGenerator> internalSnapshotGeneratorFactory,
			BiFunction<FhirContext, IValidationSupport, ValueSetExpander> internalValueSetExpanderFactory,
			TerminologyServerClient terminologyServerClient)
	{
		this(fhirContext, internalSnapshotGeneratorFactory, internalValueSetExpanderFactory, terminologyServerClient,
				DEFAULT_VALUE_SET_BINDING_STRENGTHS);
	}

	/**
	 * @param fhirContext
	 *            not <code>null</code>
	 * @param internalSnapshotGeneratorFactory
	 *            not <code>null</code>
	 * @param internalValueSetExpanderFactory
	 *            not <code>null</code>
	 * @param terminologyServerClient
	 *            not <code>null</code>
	 * @param valueSetBindingStrengths
	 *            not <code>null</code>
	 */
	public ValidatorFactoryImpl(FhirContext fhirContext,
			BiFunction<FhirContext, IValidationSupport, SnapshotGenerator> internalSnapshotGeneratorFactory,
			BiFunction<FhirContext, IValidationSupport, ValueSetExpander> internalValueSetExpanderFactory,
			TerminologyServerClient terminologyServerClient, EnumSet<BindingStrength> valueSetBindingStrengths)
	{
		this.fhirContext = fhirContext;
		this.internalSnapshotGeneratorFactory = internalSnapshotGeneratorFactory;
		this.internalValueSetExpanderFactory = internalValueSetExpanderFactory;
		this.terminologyServerClient = terminologyServerClient;
		this.valueSetBindingStrengths = valueSetBindingStrengths;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(fhirContext, "fhirContext");
		Objects.requireNonNull(internalSnapshotGeneratorFactory, "internalSnapshotGeneratorFactory");
		Objects.requireNonNull(internalValueSetExpanderFactory, "internalValueSetExpanderFactory");
		Objects.requireNonNull(terminologyServerClient, "terminologyServerClient");
		Objects.requireNonNull(valueSetBindingStrengths, "valueSetBindingStrengths");
	}

	@Override
	public BundleValidator createBundleValidator(IValidationSupport validationSupport,
			ValidationPackageWithDepedencies packageWithDependencies)
	{
		Objects.requireNonNull(validationSupport, "validationSupport");
		Objects.requireNonNull(packageWithDependencies, "packageWithDependencies");

		BundleValidatorImpl validator = new BundleValidatorImpl(
				new ResourceValidatorImpl(fhirContext, validationSupport), fhirContext,
				Collections.singletonList(packageWithDependencies));

		return validator;
	}

	@Override
	public BundleValidator createBundleValidator(IValidationSupport validationSupport,
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies)
	{
		Objects.requireNonNull(validationSupport, "validationSupport");

		BundleValidatorImpl validator = new BundleValidatorImpl(
				new ResourceValidatorImpl(fhirContext, validationSupport), fhirContext, packagesWithDependencies);

		return validator;
	}

	@Override
	public IValidationSupport expandValueSetsAndGenerateStructureDefinitionSnapshots(
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies)
	{
		List<ValueSet> expandedValueSets = new ArrayList<>();
		for (ValidationPackageWithDepedencies packageWithDependencies : packagesWithDependencies)
		{
			packageWithDependencies.parseResources(fhirContext);
			expandedValueSets.addAll(withExpandedValueSets(packageWithDependencies));
		}

		return withSnapshots(expandedValueSets, packagesWithDependencies);
	}

	private List<ValueSet> withExpandedValueSets(ValidationPackageWithDepedencies packageWithDependencies)
	{
		List<ValueSet> expandedValueSets = new ArrayList<>();
		ValueSetExpander expander = internalValueSetExpanderFactory.apply(fhirContext,
				createSupportChain(fhirContext, new ValidationSupportWithCustomResources(fhirContext, null, null, null),
						Collections.singletonList(packageWithDependencies)));

		packageWithDependencies.getValueSetsIncludingDependencies(valueSetBindingStrengths, fhirContext).forEach(v ->
		{
			logger.debug("Expanding ValueSet {}|{}", v.getUrl(), v.getVersion());

			// ValueSet uses filter or import in compose
			if (v.hasCompose() && ((v.getCompose().hasInclude()
					&& (v.getCompose().getInclude().stream().anyMatch(c -> c.hasFilter() || c.hasValueSet())))
					|| (v.getCompose().hasExclude()
							&& v.getCompose().getExclude().stream().anyMatch(c -> c.hasFilter() || c.hasValueSet()))))
			{
				expandExternal(expandedValueSets, v);
			}
			else
			{
				// will try external expansion if internal not successful
				expandInternal(expandedValueSets, expander, v);
			}
		});

		return expandedValueSets;
	}

	private void expandExternal(List<ValueSet> expandedValueSets, ValueSet v)
	{
		try
		{
			ValueSet expansion = terminologyServerClient.expand(v);
			expandedValueSets.add(expansion);
		}
		catch (WebApplicationException e)
		{
			logger.warn(
					"Error while expanding ValueSet {}|{} externally, this may result in incomplete validation: {} - {}",
					v.getUrl(), v.getVersion(), e.getClass().getName(), e.getMessage());
			getOutcome(e).ifPresent(m -> logger.debug("Expansion error response: {}", m));
			logger.debug("ValueSet with error while expanding: {}",
					fhirContext.newJsonParser().encodeResourceToString(v));
		}
		catch (Exception e)
		{
			logger.warn(
					"Error while expanding ValueSet {}|{} externally, this may result in incomplete validation: {} - {}",
					v.getUrl(), v.getVersion(), e.getClass().getName(), e.getMessage());
			logger.debug("ValueSet with error while expanding: {}",
					fhirContext.newJsonParser().encodeResourceToString(v));
		}
	}

	private void expandInternal(List<ValueSet> expandedValueSets, ValueSetExpander expander, ValueSet v)
	{
		try
		{
			ValueSetExpansionOutcome expansion = expander.expand(v);

			if (expansion.getError() != null)
				logger.warn("Error while expanding ValueSet {}|{} internally: {}", v.getUrl(), v.getVersion(),
						expansion.getError());
			else
				expandedValueSets.add(expansion.getValueset());
		}
		catch (Exception e)
		{
			logger.info(
					"Error while expanding ValueSet {}|{} internally: {} - {}, trying to expand via external terminology server next",
					v.getUrl(), v.getVersion(), e.getClass().getName(), e.getMessage());

			expandExternal(expandedValueSets, v);
		}
	}

	private Optional<String> getOutcome(WebApplicationException e)
	{
		if (e.getResponse().hasEntity())
		{
			String response = e.getResponse().readEntity(String.class);
			return Optional.of(response);
		}
		else
			return Optional.empty();
	}

	private IValidationSupport withSnapshots(List<ValueSet> expandedValueSets,
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies)
	{
		Map<String, StructureDefinition> snapshots = new HashMap<>();

		ValidationSupportWithCustomResources snapshotsAndExpandedValueSets = new ValidationSupportWithCustomResources(
				fhirContext, null, null, expandedValueSets);
		ValidationSupportChain supportChain = createSupportChain(fhirContext, snapshotsAndExpandedValueSets,
				packagesWithDependencies);

		SnapshotGenerator generator = internalSnapshotGeneratorFactory.apply(fhirContext, supportChain);

		for (ValidationPackageWithDepedencies packageWithDependencies : packagesWithDependencies)
		{
			packageWithDependencies.getValidationSupportResources().getStructureDefinitions().stream()
					.filter(s -> s.hasDifferential() && !s.hasSnapshot())
					.forEach(diff -> createSnapshot(packageWithDependencies, snapshotsAndExpandedValueSets, snapshots,
							generator, diff));
		}

		return supportChain;
	}

	private void createSnapshot(ValidationPackageWithDepedencies packageWithDependencies,
			ValidationSupportWithCustomResources snapshotsAndExpandedValueSets,
			Map<String, StructureDefinition> snapshots, SnapshotGenerator generator, StructureDefinition diff)
	{
		if (snapshots.containsKey(diff.getUrl() + "|" + diff.getVersion()))
			return;

		List<StructureDefinition> definitions = new ArrayList<>();
		definitions.addAll(packageWithDependencies.getStructureDefinitionDependencies(diff));
		definitions.add(diff);

		logger.debug("Generating snapshot for {}|{}, base {}, dependencies {}", diff.getUrl(), diff.getVersion(),
				diff.getBaseDefinition(),
				definitions.stream()
						.filter(sd -> !sd.equals(diff) && !sd.getUrl().equals(diff.getBaseDefinition())
								&& !(sd.getUrl() + "|" + sd.getVersion()).equals(diff.getBaseDefinition()))
						.map(sd -> sd.getUrl() + "|" + sd.getVersion()).distinct().sorted()
						.collect(Collectors.joining(", ", "[", "]")));

		String dependenciesWithDifferentStatus = definitions.stream()
				.filter(sd -> !sd.equals(diff) && !sd.getUrl().equals(diff.getBaseDefinition())
						&& !(sd.getUrl() + "|" + sd.getVersion()).equals(diff.getBaseDefinition()))
				.filter(sd -> !sd.getStatus().equals(diff.getStatus()))
				.map(sd -> sd.getUrl() + "|" + sd.getVersion() + ": " + sd.getStatus().toCode()).distinct().sorted()
				.collect(Collectors.joining(", "));

		if (PublicationStatus.ACTIVE.equals(diff.getStatus()) && !dependenciesWithDifferentStatus.isEmpty())
		{
			logger.warn("StructureDefinition {}|{}, has dependencies with no active status [{}]", diff.getUrl(),
					diff.getVersion(), dependenciesWithDifferentStatus);
		}

		definitions.stream().filter(sd -> sd.hasDifferential() && !sd.hasSnapshot()
				&& !snapshots.containsKey(sd.getUrl() + "|" + sd.getVersion())).forEach(sd ->
				{
					try
					{
						logger.debug("Generating snapshot for {}|{}", sd.getUrl(), sd.getVersion());
						SnapshotWithValidationMessages snapshot = generator.generateSnapshot(sd);

						if (snapshot.getSnapshot().hasSnapshot())
						{
							snapshots.put(snapshot.getSnapshot().getUrl() + "|" + snapshot.getSnapshot().getVersion(),
									snapshot.getSnapshot());
							snapshotsAndExpandedValueSets.addOrReplace(snapshot.getSnapshot());
						}
						else
							logger.error(
									"Error while generating snapshot for {}|{}: Not snaphsot returned from generator",
									diff.getUrl(), diff.getVersion());

						snapshot.getMessages().forEach(m ->
						{
							if (EnumSet.of(IssueSeverity.FATAL, IssueSeverity.ERROR, IssueSeverity.WARNING)
									.contains(m.getLevel()))
								logger.warn("{}|{} {}: {}", diff.getUrl(), diff.getVersion(), m.getLevel(),
										m.toString());
							else
								logger.info("{}|{} {}: {}", diff.getUrl(), diff.getVersion(), m.getLevel(),
										m.toString());
						});
					}
					catch (Exception e)
					{
						logger.error("Error while generating snapshot for {}|{}: {} - {}", diff.getUrl(),
								diff.getVersion(), e.getClass().getName(), e.getMessage());
					}
				});

		logger.debug("Generating snapshot for {}|{} [Done]", diff.getUrl(), diff.getVersion());
	}

	private ValidationSupportChain createSupportChain(FhirContext context,
			IValidationSupport snapshotsAndExpandedValueSets,
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies)
	{
		return new ValidationSupportChain(new CodeValidatorForExpandedValueSets(context),
				new InMemoryTerminologyServerValidationSupport(context), snapshotsAndExpandedValueSets,
				new ValidationSupportWithCustomResources(context,
						getAll(ValidationPackageWithDepedencies::getAllStructureDefinitions, packagesWithDependencies),
						getAll(ValidationPackageWithDepedencies::getAllCodeSystems, packagesWithDependencies),
						getAll(ValidationPackageWithDepedencies::getAllValueSets, packagesWithDependencies)),
				new DefaultProfileValidationSupport(context), new QuietCommonCodeSystemsTerminologyService(context),
				// TODO remove NonValidatingValidationSupport
				new NonValidatingValidationSupport(context, "http://fhir.de/CodeSystem/bfarm/icd-10-gm",
						"http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://fhir.de/CodeSystem/bfarm/ops",
						"http://fhir.de/CodeSystem/dimdi/ops", "http://fhir.de/CodeSystem/ifa/pzn",
						"http://snomed.info/sct", "http://loinc.org", "http://varnomen.hgvs.org"));
	}

	private <V> List<V> getAll(Function<ValidationPackageWithDepedencies, List<V>> mapper,
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies)
	{
		return packagesWithDependencies.stream().map(mapper).flatMap(List::stream).toList();
	}
}
