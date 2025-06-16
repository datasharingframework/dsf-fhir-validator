package dev.dsf.fhir.validator.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.hl7.fhir.r4.model.Enumerations.BindingStrength;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.context.support.IValidationSupport;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageWithDepedencies;

public interface ValidatorFactory
{
	/**
	 * Will try to generate snapshots for all {@link StructureDefinition}s of the root package and its dependencies,
	 * will try to expand all {@link ValueSet}s with binding strength {@link BindingStrength#EXTENSIBLE},
	 * {@link BindingStrength#PREFERRED} or {@link BindingStrength#REQUIRED} used by the {@link StructureDefinition} of
	 * the root package or their dependencies, before returning a {@link IValidationSupport}.
	 *
	 * @param packagesWithDependencies
	 * @return validation support for the validator
	 */
	default IValidationSupport expandValueSetsAndGenerateStructureDefinitionSnapshots(
			ValidationPackageWithDepedencies... packagesWithDependencies)
	{
		return expandValueSetsAndGenerateStructureDefinitionSnapshots(Arrays.asList(packagesWithDependencies));
	}

	/**
	 * Will try to generate snapshots for all {@link StructureDefinition}s of the root package and its dependencies,
	 * will try to expand all {@link ValueSet}s with binding strength {@link BindingStrength#EXTENSIBLE},
	 * {@link BindingStrength#PREFERRED} or {@link BindingStrength#REQUIRED} used by the {@link StructureDefinition} of
	 * the root package or their dependencies, before returning a {@link IValidationSupport}.
	 *
	 * @param packagesWithDependencies
	 *            not <code>null</code>
	 * @return validation support for the validator
	 */
	IValidationSupport expandValueSetsAndGenerateStructureDefinitionSnapshots(
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies);

	/**
	 * @param validationSupport
	 *            not <code>null</code>
	 * @param packageWithDependencies
	 *            not <code>null</code>
	 * @return {@link BundleValidator} for the given {@link IValidationSupport} and
	 *         {@link ValidationPackageWithDepedencies}
	 */
	BundleValidator createBundleValidator(IValidationSupport validationSupport,
			ValidationPackageWithDepedencies packageWithDependencies);

	/**
	 * @param validationSupport
	 *            not <code>null</code>
	 * @param packagesWithDependencies
	 * @return {@link BundleValidator} for the given {@link IValidationSupport} and
	 *         {@link ValidationPackageWithDepedencies}
	 */
	default BundleValidator createBundleValidator(IValidationSupport validationSupport,
			ValidationPackageWithDepedencies... packagesWithDependencies)
	{
		return createBundleValidator(validationSupport, List.of(packagesWithDependencies));
	}

	/**
	 * @param validationSupport
	 *            not <code>null</code>
	 * @param packagesWithDependencies
	 *            not <code>null</code>
	 * @return {@link BundleValidator} for the given {@link IValidationSupport} and
	 *         {@link ValidationPackageWithDepedencies}
	 */
	BundleValidator createBundleValidator(IValidationSupport validationSupport,
			Collection<? extends ValidationPackageWithDepedencies> packagesWithDependencies);
}
