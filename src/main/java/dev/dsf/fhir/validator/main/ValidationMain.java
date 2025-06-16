package dev.dsf.fhir.validator.main;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageIdentifier;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageManager;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageWithDepedencies;
import dev.dsf.fhir.validator.main.ValidationConfig.TerminologyServerConnectionTestStatus;
import dev.dsf.fhir.validator.service.BundleValidator;
import dev.dsf.fhir.validator.service.ValidatorFactory;

public class ValidationMain implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValidationMain.class);

	private static final class FileNameAndResource
	{
		final String filename;
		final Resource resource;

		FileNameAndResource(String filename, Resource resource)
		{
			this.filename = filename;
			this.resource = resource;
		}

		String getFilename()
		{
			return filename;
		}

		Resource getResource()
		{
			return resource;
		}
	}

	public static enum Output
	{
		JSON, XML
	}

	public static void main(String[] args)
	{
		if (args.length == 0)
		{
			logger.warn("No files to validated specified");
			System.exit(1);
		}

		try (AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext(
				ValidationConfig.class))
		{
			ValidationConfig config = springContext.getBean(ValidationConfig.class);
			TerminologyServerConnectionTestStatus status = config.testConnectionToTerminologyServer();

			if (TerminologyServerConnectionTestStatus.OK.equals(status))
			{
				ValidationMain main = springContext.getBean(ValidationMain.class);

				BundleValidator bundleValidator = main.createBundleValidator();

				main.validate(bundleValidator, args);
			}
		}
		catch (Exception e)
		{
			logger.error("", e);
			System.exit(1);
		}
	}

	private final FhirContext fhirContext;
	private final ValidationPackageManager packageManager;
	private final ValidatorFactory validatorFactory;

	private final List<ValidationPackageIdentifier> validationPackageIdentifiers = new ArrayList<>();
	private final Output output;
	private final boolean outputPretty;

	public ValidationMain(FhirContext fhirContext, ValidationPackageManager packageManager,
			ValidatorFactory validatorFactory, List<ValidationPackageIdentifier> validationPackageIdentifiers,
			Output output, boolean outputPretty)
	{
		this.fhirContext = fhirContext;
		this.packageManager = packageManager;
		this.validatorFactory = validatorFactory;

		if (validationPackageIdentifiers != null)
			this.validationPackageIdentifiers.addAll(validationPackageIdentifiers);

		this.output = output;
		this.outputPretty = outputPretty;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(fhirContext, "fhirContext");
		Objects.requireNonNull(packageManager, "packageManager");
		Objects.requireNonNull(validatorFactory, "validatorFactory");

		Objects.requireNonNull(output, "output");
	}

	private BundleValidator createBundleValidator()
	{
		logger.info("Downloading FHIR validation packages {} and dependencies",
				validationPackageIdentifiers.toString());
		List<ValidationPackageWithDepedencies> packagesWithDependencies = packageManager
				.downloadPackagesWithDependencies(
						validationPackageIdentifiers.toArray(ValidationPackageIdentifier[]::new));

		logger.info("Expanding ValueSets and generating StructureDefinition snapshots");
		IValidationSupport validationSupport = validatorFactory
				.expandValueSetsAndGenerateStructureDefinitionSnapshots(packagesWithDependencies);

		return validatorFactory.createBundleValidator(validationSupport, packagesWithDependencies);
	}

	public void validate(BundleValidator validator, String[] files)
	{
		logger.info("Using validation packages {}", validationPackageIdentifiers);

		Arrays.stream(files).map(this::read).filter(r -> r != null).forEach(r ->
		{
			logger.info("Validating {} from {}", r.getResource().getResourceType().name(), r.getFilename());

			if (r.getResource() instanceof Bundle)
			{
				Bundle validationResult = validator.validate((Bundle) r.getResource());
				System.out.println(getOutputParser().encodeResourceToString(validationResult));
			}
			else
			{
				ValidationResult validationResult = validator.validate(r.getResource());
				System.out.println(getOutputParser().encodeResourceToString(validationResult.toOperationOutcome()));
			}
		});
	}

	private IParser getOutputParser()
	{
		switch (output)
		{
			case JSON:
				return fhirContext.newJsonParser().setPrettyPrint(outputPretty);
			case XML:
				return fhirContext.newXmlParser().setPrettyPrint(outputPretty);
			default:
				throw new IllegalStateException("Output of type " + output + " not supported");
		}
	}

	private FileNameAndResource read(String file)
	{
		if (file.endsWith(".json"))
			return tryJson(file);
		else if (file.endsWith(".xml"))
			return tryXml(file);
		else
		{
			logger.warn("File {} not supported, filename needs to end with .json or .xml", file);
			return null;
		}
	}

	private FileNameAndResource tryJson(String file)
	{
		try (InputStream in = Files.newInputStream(Paths.get(file)))
		{
			IBaseResource resource = fhirContext.newJsonParser().parseResource(in);
			logger.info("{} read from {}", resource.getClass().getSimpleName(), file);
			return new FileNameAndResource(file, (Resource) resource);
		}
		catch (Exception e)
		{
			logger.warn("Unable to read {} as JSON, {}: {}", file, e.getClass().getName(), e.getMessage());
			return null;
		}
	}

	private FileNameAndResource tryXml(String file)
	{
		try (InputStream in = Files.newInputStream(Paths.get(file)))
		{
			IBaseResource resource = fhirContext.newXmlParser().parseResource(in);
			logger.info("{} read from {}", resource.getClass().getSimpleName(), file);
			return new FileNameAndResource(file, (Resource) resource);
		}
		catch (Exception e)
		{
			logger.warn("Unable to read {} as XML, {}: {}", file, e.getClass().getName(), e.getMessage());
			return null;
		}
	}
}
