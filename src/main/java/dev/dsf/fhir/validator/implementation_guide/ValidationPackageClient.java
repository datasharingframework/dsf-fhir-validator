package dev.dsf.fhir.validator.implementation_guide;

import java.io.IOException;

import jakarta.ws.rs.WebApplicationException;

public interface ValidationPackageClient
{
	/**
	 * @param name
	 *            not <code>null</code>
	 * @param version
	 *            not <code>null</code>
	 * @return downloaded {@link ValidationPackage}, never <code>null</code>
	 * @throws IOException
	 * @throws WebApplicationException
	 */
	default ValidationPackage download(String name, String version) throws IOException, WebApplicationException
	{
		return download(new ValidationPackageIdentifier(name, version));
	}

	/**
	 * @param identifier
	 *            not <code>null</code>
	 * @return downloaded {@link ValidationPackage}, never <code>null</code>
	 * @throws IOException
	 * @throws WebApplicationException
	 */
	ValidationPackage download(ValidationPackageIdentifier identifier) throws IOException, WebApplicationException;

	/**
	 * @param name
	 *            not <code>null</code>
	 * @return package versions
	 * @throws WebApplicationException
	 */
	PackageVersions list(String name) throws WebApplicationException;
}