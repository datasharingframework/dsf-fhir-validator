package dev.dsf.fhir.validator.implementation_guide;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public interface ValidationPackageManager
{
	/**
	 * Downloads the given FHIR package and all its dependencies.
	 *
	 * @param name
	 *            not <code>null</code>
	 * @param version
	 *            not <code>null</code>
	 * @return validation package with dependencies
	 */
	default ValidationPackageWithDepedencies downloadPackageWithDependencies(String name, String version)
	{
		return downloadPackageWithDependencies(new ValidationPackageIdentifier(name, version));
	}

	/**
	 * Downloads the given FHIR package and all its dependencies.
	 *
	 * @param identifier
	 * @return validation package with dependencies
	 */
	default ValidationPackageWithDepedencies downloadPackageWithDependencies(ValidationPackageIdentifier identifier)
	{
		return downloadPackagesWithDependencies(identifier).get(0);
	}

	/**
	 * Downloads the given FHIR packages and all its dependencies.
	 *
	 * @param identifiers
	 * @return unmodifiable list of {@link ValidationPackageWithDepedencies}
	 */
	default List<ValidationPackageWithDepedencies> downloadPackagesWithDependencies(
			ValidationPackageIdentifier... identifiers)
	{
		return downloadPackagesWithDependencies(Arrays.asList(identifiers));
	}

	/**
	 * Downloads the given FHIR packages and all its dependencies.
	 *
	 * @param identifiers
	 * @return unmodifiable list of {@link ValidationPackageWithDepedencies}
	 */
	List<ValidationPackageWithDepedencies> downloadPackagesWithDependencies(
			Collection<? extends ValidationPackageIdentifier> identifiers);

}