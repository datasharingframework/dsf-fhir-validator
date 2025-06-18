package dev.dsf.fhir.validator.implementation_guide;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import jakarta.ws.rs.WebApplicationException;

public class ValidationPackageManagerImpl implements InitializingBean, ValidationPackageManager
{
	private static final Logger logger = LoggerFactory.getLogger(ValidationPackageManagerImpl.class);

	public static final List<ValidationPackageIdentifier> DEFAULT_NO_PACKAGE_DOWNLOAD_LIST = List
			.of(new ValidationPackageIdentifier("hl7.fhir.r4.core", "4.0.1"));

	private final ValidationPackageClient validationPackageClient;

	private final ObjectMapper mapper;
	private final FhirContext fhirContext;

	private final List<ValidationPackageIdentifier> noDownloadPackages = new ArrayList<>();


	public ValidationPackageManagerImpl(ValidationPackageClient validationPackageClient, ObjectMapper mapper,
			FhirContext fhirContext)
	{
		this(validationPackageClient, mapper, fhirContext, DEFAULT_NO_PACKAGE_DOWNLOAD_LIST);
	}

	public ValidationPackageManagerImpl(ValidationPackageClient validationPackageClient, ObjectMapper mapper,
			FhirContext fhirContext, Collection<ValidationPackageIdentifier> noDownloadPackages)
	{
		this.validationPackageClient = validationPackageClient;
		this.mapper = mapper;
		this.fhirContext = fhirContext;

		if (noDownloadPackages != null)
			this.noDownloadPackages.addAll(noDownloadPackages);
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(validationPackageClient, "validationPackageClient");
		Objects.requireNonNull(mapper, "mapper");
		Objects.requireNonNull(fhirContext, "fhirContext");
	}

	@Override
	public List<ValidationPackageWithDepedencies> downloadPackagesWithDependencies(
			Collection<? extends ValidationPackageIdentifier> identifiers)
	{
		Map<ValidationPackageIdentifier, ValidationPackage> allPackagesByNameAndVersion = new HashMap<>();

		List<ValidationPackageWithDepedencies> packages = new ArrayList<>();
		for (ValidationPackageIdentifier identifier : identifiers)
		{
			Map<ValidationPackageIdentifier, ValidationPackage> packagesByNameAndVersion = new HashMap<>();
			downloadPackageWithDependencies(identifier, packagesByNameAndVersion, allPackagesByNameAndVersion);
			packages.add(ValidationPackageWithDepedencies.from(packagesByNameAndVersion, identifier));
		}

		logger.info("Validating with packages {}",
				packages.stream().map(p -> p.getIdentifier().toString() + " {dependencies: "
						+ p.getDependencies().stream().map(ValidationPackage::getIdentifier)
								.map(ValidationPackageIdentifier::toString).sorted().collect(Collectors.joining(", "))
						+ "}").collect(Collectors.joining(", ", "[", "]")));

		return packages;
	}

	private void downloadPackageWithDependencies(ValidationPackageIdentifier identifier,
			Map<ValidationPackageIdentifier, ValidationPackage> packagesByNameAndVersion,
			Map<ValidationPackageIdentifier, ValidationPackage> allPackagesByNameAndVersion)
	{
		if (allPackagesByNameAndVersion.containsKey(identifier))
		{
			ValidationPackage dependency = allPackagesByNameAndVersion.get(identifier);
			packagesByNameAndVersion.put(identifier, dependency);

			// already downloaded
			return;
		}
		else if (noDownloadPackages.contains(identifier))
		{
			logger.debug("Not downloading package {}", identifier.toString());
			return;
		}

		ValidationPackage vPackage = downloadAndHandleException(identifier);
		identifier = vPackage.getIdentifier();

		// check again, as the identifier may have changed from a A.B.x wildcard
		if (allPackagesByNameAndVersion.containsKey(identifier))
		{
			ValidationPackage dependency = allPackagesByNameAndVersion.get(identifier);
			packagesByNameAndVersion.put(identifier, dependency);

			// already downloaded
			return;
		}
		else if (noDownloadPackages.contains(identifier))
		{
			logger.debug("Not using package {}", identifier.toString());
			return;
		}

		packagesByNameAndVersion.put(identifier, vPackage);
		allPackagesByNameAndVersion.put(identifier, vPackage);

		ValidationPackageDescriptor descriptor = getDescriptorAndHandleException(vPackage);
		descriptor.getDependencyIdentifiers().forEach(
				i -> downloadPackageWithDependencies(i, packagesByNameAndVersion, allPackagesByNameAndVersion));
	}

	private ValidationPackage downloadAndHandleException(ValidationPackageIdentifier identifier)
	{
		if (identifier.version().matches("\\d+\\.\\d+\\.x"))
		{
			String versoinPrefix = identifier.version().substring(0, identifier.version().length() - 1);

			PackageVersions versions = validationPackageClient.list(identifier.name());
			Optional<String> latest = versions.getLatest(versoinPrefix);

			if (latest.isPresent())
				identifier = new ValidationPackageIdentifier(identifier.name(), latest.get());
		}

		try
		{
			logger.debug("Downloading validation package {}", identifier);
			return validationPackageClient.download(identifier);
		}
		catch (WebApplicationException | IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private ValidationPackageDescriptor getDescriptorAndHandleException(ValidationPackage vPackage)
	{
		try
		{
			return vPackage.getDescriptor(mapper);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
