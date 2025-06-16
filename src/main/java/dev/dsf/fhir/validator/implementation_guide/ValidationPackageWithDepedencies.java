package dev.dsf.fhir.validator.implementation_guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r4.model.Enumerations.BindingStrength;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionDifferentialComponent;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;

public class ValidationPackageWithDepedencies extends ValidationPackage
{
	private static final Logger logger = LoggerFactory.getLogger(ValidationPackageWithDepedencies.class);

	public static ValidationPackageWithDepedencies from(
			Map<ValidationPackageIdentifier, ValidationPackage> packagesByNameAndVersion,
			ValidationPackageIdentifier rootPackageIdentifier)
	{
		Objects.requireNonNull(packagesByNameAndVersion, "packagesByNameAndVersion");
		Objects.requireNonNull(rootPackageIdentifier, "rootPackageIdentifier");

		ValidationPackage rootPackage = packagesByNameAndVersion.get(rootPackageIdentifier);
		if (rootPackage == null)
			throw new IllegalArgumentException("root package not part of given map");

		List<ValidationPackage> packages = packagesByNameAndVersion.entrySet().stream()
				.filter(e -> !rootPackageIdentifier.equals(e.getKey())).map(Entry::getValue)
				.collect(Collectors.toList());

		return new ValidationPackageWithDepedencies(rootPackage, packages);
	}

	private final List<ValidationPackage> dependencies = new ArrayList<>();

	private Map<String, List<StructureDefinition>> structureDefinitionsByUrl;
	private Map<String, StructureDefinition> structureDefinitionsByUrlAndVersion;

	public ValidationPackageWithDepedencies(ValidationPackage validationPackage, List<ValidationPackage> dependencies)
	{
		super(validationPackage.getName(), validationPackage.getVersion(), validationPackage.getEntries());

		if (dependencies != null)
			this.dependencies.addAll(dependencies);
	}

	public List<ValidationPackage> getDependencies()
	{
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void parseResources(FhirContext context)
	{
		super.parseResources(context);

		getDependencies().forEach(p -> p.parseResources(context));
	}

	private <R extends MetadataResource> List<R> getAll(Function<ValidationSupportResources, List<R>> accessor)
	{
		return Stream.concat(Stream.of(this), getDependencies().stream())
				.map(ValidationPackage::getValidationSupportResources).map(accessor).flatMap(List::stream)
				.collect(Collectors.toList());
	}

	public List<CodeSystem> getAllCodeSystems()
	{
		return getAll(ValidationSupportResources::getCodeSystems);
	}

	public List<NamingSystem> getAllNamingSystems()
	{
		return getAll(ValidationSupportResources::getNamingSystems);
	}

	public List<StructureDefinition> getAllStructureDefinitions()
	{
		return getAll(ValidationSupportResources::getStructureDefinitions);
	}

	public List<ValueSet> getAllValueSets()
	{
		return getAll(ValidationSupportResources::getValueSets);
	}

	public ValidationSupportResources getAllValidationSupportResources()
	{
		return new ValidationSupportResources(getAllCodeSystems(), getAllNamingSystems(), getAllStructureDefinitions(),
				getAllValueSets());
	}

	private Map<String, List<StructureDefinition>> getStructureDefinitionsByUrl()
	{
		if (structureDefinitionsByUrl == null)
			structureDefinitionsByUrl = getAllStructureDefinitions().stream().filter(StructureDefinition::hasUrl)
					.collect(Collectors.toMap(StructureDefinition::getUrl, Collections::singletonList, (sd1, sd2) ->
					{
						List<StructureDefinition> sds = new ArrayList<>();
						sds.addAll(sd1);
						sds.addAll(sd2);
						return sds;
					}));

		return structureDefinitionsByUrl;
	}

	private Map<String, StructureDefinition> getStructureDefinitionsByUrlAndVersion()
	{
		if (structureDefinitionsByUrlAndVersion == null)
			structureDefinitionsByUrlAndVersion = getAllStructureDefinitions().stream()
					.filter(StructureDefinition::hasUrl).filter(StructureDefinition::hasVersion)
					.collect(Collectors.toMap(s -> s.getUrl() + "|" + s.getVersion(), Function.identity()));

		return structureDefinitionsByUrlAndVersion;
	}

	public List<StructureDefinition> getStructureDefinitionDependencies(StructureDefinition structureDefinition)
	{
		return doGetDependencies(structureDefinition, new HashSet<>());
	}

	private List<StructureDefinition> doGetDependencies(StructureDefinition structureDefinition, Set<String> visited)
	{
		if (visited.contains(structureDefinition.getUrl())
				|| visited.contains(structureDefinition.getUrl() + "|" + structureDefinition.getVersion()))
			return Collections.emptyList();
		else
		{
			visited.add(structureDefinition.getUrl());
			visited.add(structureDefinition.getUrl() + "|" + structureDefinition.getVersion());
		}

		List<StructureDefinition> dependencies = new ArrayList<>();
		Set<StructureDefinition> baseDefinitions = getStructureDefinitionsByUrl(
				structureDefinition.getBaseDefinition());

		baseDefinitions.forEach(sd -> dependencies.addAll(doGetDependencies(sd, visited)));
		dependencies.addAll(baseDefinitions);

		structureDefinition.getDifferential().getElement().stream().forEach(e ->
		{
			if (e.hasPath() && "Extension.url".equals(e.getPath()) && e.hasFixed() && e.getFixed() instanceof UriType)
			{
				UriType t = (UriType) e.getFixed();
				Set<StructureDefinition> extensions = getStructureDefinitionsByUrl(t.getValue());
				extensions.forEach(sd -> dependencies.addAll(doGetDependencies(sd, visited)));
				dependencies.addAll(extensions);
			}

			if (e.hasType())
			{
				e.getType().forEach(t ->
				{
					if (t.hasProfile())
					{
						t.getProfile().forEach(p ->
						{
							Set<StructureDefinition> profiles = getStructureDefinitionsByUrl(p.getValue());
							profiles.forEach(sd -> dependencies.addAll(doGetDependencies(sd, visited)));
							dependencies.addAll(profiles);
						});
					}

					if (t.hasTargetProfile())
					{
						t.getTargetProfile().forEach(p ->
						{
							Set<StructureDefinition> targetProfiles = getStructureDefinitionsByUrl(p.getValue());
							targetProfiles.forEach(sd -> dependencies.addAll(doGetDependencies(sd, visited)));
							dependencies.addAll(targetProfiles);
						});
					}
				});
			}
		});

		return dependencies;
	}

	private Set<StructureDefinition> getStructureDefinitionsByUrl(String sdUrl)
	{
		Set<StructureDefinition> sds = new HashSet<>();

		List<StructureDefinition> byUrl = getStructureDefinitionsByUrl().get(sdUrl);
		if (byUrl != null)
			sds.addAll(byUrl);

		StructureDefinition byUrlAndVersion = getStructureDefinitionsByUrlAndVersion().get(sdUrl);
		if (byUrlAndVersion != null)
			sds.add(byUrlAndVersion);

		return sds;
	}

	// TODO what about ValueSets from base profiles ?
	private Set<String> findValueSetsWithBindingStrength(Stream<StructureDefinition> sds,
			EnumSet<BindingStrength> bindingStrengths)
	{
		return sds.filter(StructureDefinition::hasDifferential).map(StructureDefinition::getDifferential)
				.filter(StructureDefinitionDifferentialComponent::hasElement)
				.map(StructureDefinitionDifferentialComponent::getElement).flatMap(List::stream)
				.filter(ElementDefinition::hasBinding).map(ElementDefinition::getBinding)
				.filter(b -> bindingStrengths.contains(b.getStrength()))
				.filter(ElementDefinitionBindingComponent::hasValueSet)
				.map(ElementDefinitionBindingComponent::getValueSet).collect(Collectors.toSet());
	}

	public List<ValueSet> getValueSetsIncludingDependencies(EnumSet<BindingStrength> bindingStrengths,
			FhirContext fhirContext)
	{
		Stream<StructureDefinition> sds = getValidationSupportResources().getStructureDefinitions().stream()
				.flatMap(sd -> Stream.concat(Stream.of(sd), getStructureDefinitionDependencies(sd).stream()))
				.distinct();

		Set<String> neededValueSets = findValueSetsWithBindingStrength(sds, bindingStrengths);
		List<ValueSet> foundValueSets = getAllValueSets().stream().filter(vs -> neededValueSets.contains(vs.getUrl())
				|| neededValueSets.contains(vs.getUrl() + "|" + vs.getVersion())).collect(Collectors.toList());

		logMissingValueSets(neededValueSets, foundValueSets, fhirContext);

		return foundValueSets;
	}

	private void logMissingValueSets(Set<String> neededValueSets, List<ValueSet> foundValueSets,
			FhirContext fhirContext)
	{
		Set<String> foundValueSetUrls = foundValueSets.stream()
				.flatMap(vs -> Stream.of(vs.getUrl(), vs.getUrl() + "|" + vs.getVersion())).collect(Collectors.toSet());

		IValidationSupport validationSupport = fhirContext.getValidationSupport();
		String missingValueSets = neededValueSets.stream()
				.filter(e -> !foundValueSetUrls.contains(e) && validationSupport.fetchValueSet(e) == null).distinct()
				.sorted().collect(Collectors.joining(", "));

		if (!missingValueSets.isEmpty())
		{
			logger.warn(
					"The following ValueSet are required for validation but could not be found in validation package {}|{} or its dependencies, this may result in incomplete valdidation: [{}]",
					getName(), getVersion(), missingValueSets);
		}
	}
}
