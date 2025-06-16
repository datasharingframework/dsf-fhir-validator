package dev.dsf.fhir.validator.main;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations.BindingStrength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.i18n.HapiLocalizer;
import de.hsheilbronn.mi.utils.crypto.io.PemReader;
import de.hsheilbronn.mi.utils.crypto.keystore.KeyStoreCreator;
import dev.dsf.fhir.validator.client.TerminologyServerClient;
import dev.dsf.fhir.validator.client.TerminologyServerClientJersey;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageClient;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageClientJersey;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageClientWithFileSystemCache;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageIdentifier;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageManager;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageManagerImpl;
import dev.dsf.fhir.validator.main.ValidationMain.Output;
import dev.dsf.fhir.validator.service.ValidatorFactory;
import dev.dsf.fhir.validator.service.ValidatorFactoryImpl;
import dev.dsf.fhir.validator.structure_definition.SnapshotGenerator;
import dev.dsf.fhir.validator.structure_definition.SnapshotGeneratorImpl;
import dev.dsf.fhir.validator.structure_definition.SnapshotGeneratorWithFileSystemCache;
import dev.dsf.fhir.validator.structure_definition.SnapshotGeneratorWithModifiers;
import dev.dsf.fhir.validator.structure_definition.StructureDefinitionModifier;
import dev.dsf.fhir.validator.value_set.ValueSetExpander;
import dev.dsf.fhir.validator.value_set.ValueSetExpanderImpl;
import dev.dsf.fhir.validator.value_set.ValueSetExpanderWithFileSystemCache;
import dev.dsf.fhir.validator.value_set.ValueSetExpanderWithModifiers;
import dev.dsf.fhir.validator.value_set.ValueSetExpansionClientWithFileSystemCache;
import dev.dsf.fhir.validator.value_set.ValueSetExpansionClientWithModifiers;
import dev.dsf.fhir.validator.value_set.ValueSetModifier;
import jakarta.ws.rs.WebApplicationException;

@Configuration
@PropertySource(ignoreResourceNotFound = true, value = "file:application.properties")
public class ValidationConfig
{
	private static final Logger logger = LoggerFactory.getLogger(ValidationConfig.class);

	public static enum TerminologyServerConnectionTestStatus
	{
		OK, NOT_OK, DISABLED
	}

	@Value("${dev.dsf.validation:true}")
	private boolean validationEnabled;

	@Value("#{'${dev.dsf.validation.package:}'.trim().split('(,[ ]?)|(\\n)')}")
	private List<String> validationPackages;

	@Value("#{'${dev.dsf.validation.package.noDownload:}'.trim().split('(,[ ]?)|(\\n)')}")
	private List<String> noDownloadPackages;

	@Value("${dev.dsf.validation.package.cacheFolder:${java.io.tmpdir}/dsf_validation_cache/Package}")
	private String packageCacheFolder;

	@Value("${dev.dsf.validation.package.server.baseUrl:https://packages.simplifier.net}")
	private String packageServerBaseUrl;

	@Value("${dev.dsf.validation.package.client.trust.certificates:#{null}}")
	private String packageClientTrustCertificates;

	@Value("${dev.dsf.validation.package.client.authentication.certificate:#{null}}")
	private String packageClientCertificate;

	@Value("${dev.dsf.validation.package.client.authentication.certificate.private.key:#{null}}")
	private String packageClientCertificatePrivateKey;

	@Value("${dev.dsf.validation.package.client.authentication.certificate.private.key.password:#{null}}")
	private char[] packageClientCertificatePrivateKeyPassword;

	@Value("${dev.dsf.validation.package.client.authentication.basic.username:#{null}}")
	private String packageClientBasicAuthUsername;

	@Value("${dev.dsf.validation.package.client.authentication.basic.password:#{null}}")
	private char[] packageClientBasicAuthPassword;

	@Value("${dev.dsf.validation.package.client.timeout.connect:10000}")
	private int packageClientConnectTimeout;

	@Value("${dev.dsf.validation.package.client.timeout.read:300000}")
	private int packageClientReadTimeout;

	@Value("${dev.dsf.validation.package.client.verbose:false}")
	private boolean packageClientVerbose;

	@Value("#{'${dev.dsf.validation.valueset.bindingStrength:required,extensible,preferred,example}'.trim().split('(,[ ]?)|(\\n)')}")
	private List<String> valueSetExpansionBindingStrengths;

	@Value("${dev.dsf.validation.valueset.cacheFolder:${java.io.tmpdir}/dsf_validation_cache/ValueSet}")
	private String valueSetCacheFolder;

	@Value("${dev.dsf.validation.valueset.cacheDraftResources:true}")
	private boolean valueSetCacheDraftResources;

	@Value("${dev.dsf.validation.valueset.expansion.server.baseUrl:https://ontoserver.mii-termserv.de/fhir}")
	private String valueSetExpansionServerBaseUrl;

	@Value("${dev.dsf.validation.valueset.expansion.client.trust.certificates:#{null}}")
	private String valueSetExpansionClientTrustCertificates;

	@Value("${dev.dsf.validation.valueset.expansion.client.authentication.certificate:#{null}}")
	private String valueSetExpansionClientCertificate;

	@Value("${dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key:#{null}}")
	private String valueSetExpansionClientCertificatePrivateKey;

	@Value("${dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key.password:#{null}}")
	private char[] valueSetExpansionClientCertificatePrivateKeyPassword;

	@Value("${dev.dsf.validation.valueset.expansion.client.timeout.connect:10000}")
	private int valueSetExpansionClientConnectTimeout;

	@Value("${dev.dsf.validation.valueset.expansion.client.timeout.read:300000}")
	private int valueSetExpansionClientReadTimeout;

	@Value("${dev.dsf.validation.valueset.expansion.client.verbose:false}")
	private boolean valueSetExpansionClientVerbose;

	@Value("#{'${dev.dsf.validation.valueset.expansion.modifierClasses:"
			+ "dev.dsf.fhir.validator.value_set.MissingEntriesIncluder,"
			+ "dev.dsf.fhir.validator.value_set.VersionIncluder" + "}'.trim().split('(,[ ]?)|(\\n)')}")
	private List<String> valueSetModifierClasses;

	@Value("#{'${dev.dsf.validation.structuredefinition.modifierClasses:"
			+ "dev.dsf.fhir.validator.structure_definition.ClosedTypeSlicingRemover,"
			+ "dev.dsf.fhir.validator.structure_definition.SliceMinFixer" + "}'.trim().split('(,[ ]?)|(\\n)')}")
	private List<String> structureDefinitionModifierClasses;

	@Value("${dev.dsf.validation.structuredefinition.cacheFolder:${java.io.tmpdir}/dsf_validation_cache/StructureDefinition}")
	private String structureDefinitionCacheFolder;

	@Value("${dev.dsf.validation.structuredefinition.cacheDraftResources:true}")
	private boolean structureDefinitionCacheDraftResources;

	@Value("${java.io.tmpdir}")
	private String systemTempFolder;

	@Value("${dsf.dev.validation.output:JSON}")
	private Output output;

	@Value("${dsf.dev.validation.output.pretty:true}")
	private boolean outputPretty;

	@Value("${dsf.dev.validation.proxy.url:#{null}}")
	private String proxyUrl;

	@Value("${dsf.dev.validation.proxy.username:#{null}}")
	private String proxyUsername;

	@Value("${dsf.dev.validation.proxy.password:#{null}}")
	private char[] proxyPassword;

	@Bean
	public ObjectMapper objectMapper()
	{
		return JsonMapper.builder().serializationInclusion(Include.NON_NULL).serializationInclusion(Include.NON_EMPTY)
				.disable(MapperFeature.AUTO_DETECT_CREATORS).disable(MapperFeature.AUTO_DETECT_FIELDS)
				.disable(MapperFeature.AUTO_DETECT_SETTERS).build();
	}

	@Bean
	public FhirContext fhirContext()
	{
		FhirContext context = FhirContext.forR4();
		HapiLocalizer localizer = new HapiLocalizer()
		{
			@Override
			public Locale getLocale()
			{
				return Locale.ROOT;
			}
		};
		context.setLocalizer(localizer);
		return context;
	}

	@Bean
	public ValidationMain validatorMain()
	{
		return new ValidationMain(fhirContext(), validationPackageManager(), validatorFactory(),
				validationPackageIdentifiers(), output, outputPretty);
	}

	@Bean
	public ValidationPackageManager validationPackageManager()
	{
		List<ValidationPackageIdentifier> noDownload = noDownloadPackages.stream()
				.filter(Predicate.not(String::isBlank)).map(ValidationPackageIdentifier::fromString)
				.collect(Collectors.toList());

		return new ValidationPackageManagerImpl(validationPackageClient(), objectMapper(), fhirContext(), noDownload);
	}

	@Bean
	public ValidatorFactory validatorFactory()
	{
		EnumSet<BindingStrength> bindingStrengths = EnumSet.copyOf(
				valueSetExpansionBindingStrengths.stream().map(BindingStrength::fromCode).collect(Collectors.toList()));

		return new ValidatorFactoryImpl(fhirContext(), internalSnapshotGeneratorFactory(),
				internalValueSetExpanderFactory(), terminologyServerClient(), bindingStrengths);
	}

	private StructureDefinitionModifier createStructureDefinitionModifier(String className)
	{
		try
		{
			Class<?> modifierClass = Class.forName(className);
			if (StructureDefinitionModifier.class.isAssignableFrom(modifierClass))
				return (StructureDefinitionModifier) modifierClass.getConstructor().newInstance();
			else
				throw new IllegalArgumentException(
						"Class " + className + " not compatible with " + StructureDefinitionModifier.class.getName());
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Bean
	public BiFunction<FhirContext, IValidationSupport, SnapshotGenerator> internalSnapshotGeneratorFactory()
	{
		List<StructureDefinitionModifier> structureDefinitionModifiers = structureDefinitionModifierClasses.stream()
				.map(this::createStructureDefinitionModifier).collect(Collectors.toList());

		return (fc, vs) -> new SnapshotGeneratorWithFileSystemCache(structureDefinitionCacheFolder(), fc,
				new SnapshotGeneratorWithModifiers(new SnapshotGeneratorImpl(fc, vs), structureDefinitionModifiers),
				structureDefinitionCacheDraftResources);
	}

	@Bean
	public Path structureDefinitionCacheFolder()
	{
		return cacheFolder("StructureDefinition", structureDefinitionCacheFolder);
	}

	@Bean
	public BiFunction<FhirContext, IValidationSupport, ValueSetExpander> internalValueSetExpanderFactory()
	{
		return (fc, vs) -> new ValueSetExpanderWithFileSystemCache(valueSetCacheFolder(), fc,
				new ValueSetExpanderWithModifiers(new ValueSetExpanderImpl(fc, vs), valueSetModifiers()),
				valueSetCacheDraftResources);
	}

	private Path cacheFolder(String cacheFolderType, String cacheFolder)
	{
		Objects.requireNonNull(cacheFolder, "cacheFolder");
		Path cacheFolderPath = Paths.get(cacheFolder);

		try
		{
			if (cacheFolderPath.startsWith(systemTempFolder))
			{
				Files.createDirectories(cacheFolderPath);
				logger.debug("Cache folder for type {} created at {}", cacheFolderType,
						cacheFolderPath.toAbsolutePath().toString());
			}

			if (!Files.isWritable(cacheFolderPath))
				throw new IOException("Cache folder for type " + cacheFolderType + " + at "
						+ cacheFolderPath.toAbsolutePath().toString() + " not writable");
			else
				return cacheFolderPath;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private Path checkReadable(String file)
	{
		if (file == null)
			return null;
		else
		{
			Path path = Paths.get(file);

			if (!Files.isReadable(path))
				throw new RuntimeException(path.toString() + " not readable");

			return path;
		}
	}

	private KeyStore trustStore(String trustStoreType, String trustCertificatesFile)
	{
		if (trustCertificatesFile == null)
			return null;

		Path trustCertificatesPath = checkReadable(trustCertificatesFile);

		try
		{
			logger.debug("Creating trust-store for {} from {}", trustStoreType, trustCertificatesPath.toString());
			List<X509Certificate> certificates = PemReader.readCertificates(trustCertificatesPath);
			return KeyStoreCreator.jksForTrustedCertificates(certificates);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private KeyStore keyStore(String keyStoreType, String clientCertificateFile, String clientCertificatePrivateKeyFile,
			char[] clientCertificatePrivateKeyPassword, char[] keyStorePassword)
	{
		if ((clientCertificateFile != null) != (clientCertificatePrivateKeyFile != null))
			throw new IllegalArgumentException(keyStoreType + " certificate or private-key not specified");
		else if (clientCertificateFile == null && clientCertificatePrivateKeyFile == null)
			return null;

		Path clientCertificatePath = checkReadable(clientCertificateFile);
		Path clientCertificatePrivateKeyPath = checkReadable(clientCertificatePrivateKeyFile);

		try
		{
			PrivateKey privateKey = PemReader.readPrivateKey(clientCertificatePrivateKeyPath,
					clientCertificatePrivateKeyPassword);
			List<X509Certificate> certificates = PemReader.readCertificates(clientCertificatePath);

			logger.debug("Creating key-store for {} from {} and {} with password {}", keyStoreType,
					clientCertificatePath.toString(), clientCertificatePrivateKeyPath.toString(),
					clientCertificatePrivateKeyPassword != null ? "***" : "null");
			return KeyStoreCreator.jksForPrivateKeyAndCertificateChain(privateKey, keyStorePassword, certificates);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Bean
	public ValidationPackageClient validationPackageClient()
	{
		return new ValidationPackageClientWithFileSystemCache(packageCacheFolder(), objectMapper(),
				validationPackageClientJersey());
	}

	@Bean
	public Path packageCacheFolder()
	{
		return cacheFolder("Package", packageCacheFolder);
	}

	private ValidationPackageClientJersey validationPackageClientJersey()
	{
		if ((packageClientBasicAuthUsername != null) != (packageClientBasicAuthPassword != null))
		{
			throw new IllegalArgumentException(
					"Package client basic authentication username or password not specified");
		}

		KeyStore packageClientTrustStore = trustStore("FHIR package client", packageClientTrustCertificates);
		char[] packageClientKeyStorePassword = UUID.randomUUID().toString().toCharArray();
		KeyStore packageClientKeyStore = keyStore("FHIR package client", packageClientCertificate,
				packageClientCertificatePrivateKey, packageClientCertificatePrivateKeyPassword,
				packageClientKeyStorePassword);

		return new ValidationPackageClientJersey(packageServerBaseUrl, packageClientTrustStore, packageClientKeyStore,
				packageClientKeyStore == null ? null : packageClientKeyStorePassword, packageClientBasicAuthUsername,
				packageClientBasicAuthPassword, proxyUrl, proxyUsername, proxyPassword, packageClientConnectTimeout,
				packageClientReadTimeout, packageClientVerbose);
	}

	@Bean
	public TerminologyServerClient terminologyServerClient()
	{
		return new ValueSetExpansionClientWithFileSystemCache(valueSetCacheFolder(), fhirContext(),
				new ValueSetExpansionClientWithModifiers(terminologyServerClientJersey(), valueSetModifiers()),
				valueSetCacheDraftResources);
	}

	@Bean
	public List<ValueSetModifier> valueSetModifiers()
	{
		return valueSetModifierClasses.stream().map(this::createValueSetModifier).collect(Collectors.toList());
	}

	private ValueSetModifier createValueSetModifier(String className)
	{
		try
		{
			Class<?> modifierClass = Class.forName(className);
			if (ValueSetModifier.class.isAssignableFrom(modifierClass))
				return (ValueSetModifier) modifierClass.getConstructor().newInstance();
			else
				throw new IllegalArgumentException(
						"Class " + className + " not compatible with " + ValueSetModifier.class.getName());
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | SecurityException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Bean
	public Path valueSetCacheFolder()
	{
		return cacheFolder("ValueSet", valueSetCacheFolder);
	}

	private TerminologyServerClient terminologyServerClientJersey()
	{
		KeyStore valueSetExpansionClientTrustStore = trustStore("ValueSet expansion client",
				valueSetExpansionClientTrustCertificates);
		char[] valueSetExpansionClientKeyStorePassword = UUID.randomUUID().toString().toCharArray();
		KeyStore valueSetExpansionClientKeyStore = keyStore("ValueSet expansion client",
				valueSetExpansionClientCertificate, valueSetExpansionClientCertificatePrivateKey,
				valueSetExpansionClientCertificatePrivateKeyPassword, valueSetExpansionClientKeyStorePassword);

		return new TerminologyServerClientJersey(valueSetExpansionServerBaseUrl, valueSetExpansionClientTrustStore,
				valueSetExpansionClientKeyStore,
				valueSetExpansionClientKeyStore == null ? null : valueSetExpansionClientKeyStorePassword, proxyUrl,
				proxyUsername, proxyPassword, valueSetExpansionClientConnectTimeout, valueSetExpansionClientReadTimeout,
				valueSetExpansionClientVerbose, objectMapper(), fhirContext());
	}

	public TerminologyServerConnectionTestStatus testConnectionToTerminologyServer()
	{
		logger.info(
				"{} connection to terminology server with {trustStorePath: {}, certificatePath: {}, privateKeyPath: {}, privateKeyPassword: {},"
						+ " serverBase: {}}",
				validationEnabled ? "Testing" : "Not testing", valueSetExpansionClientTrustCertificates,
				valueSetExpansionClientCertificate, valueSetExpansionClientCertificatePrivateKey,
				valueSetExpansionClientCertificatePrivateKeyPassword != null ? "***" : "null",
				valueSetExpansionServerBaseUrl);

		if (!validationEnabled)
			return TerminologyServerConnectionTestStatus.DISABLED;

		try
		{
			CapabilityStatement metadata = terminologyServerClient().getMetadata();
			logger.info("Connection test OK: {} - {}", metadata.getSoftware().getName(),
					metadata.getSoftware().getVersion());
			return TerminologyServerConnectionTestStatus.OK;
		}
		catch (Exception e)
		{
			if (e instanceof WebApplicationException)
			{
				String response = ((WebApplicationException) e).getResponse().readEntity(String.class);
				logger.error("Connection test failed: {} - {}", e.getMessage(), response);
			}
			else
				logger.error("Connection test failed: {}", e.getMessage());

			return TerminologyServerConnectionTestStatus.NOT_OK;
		}
	}

	@Bean
	public List<ValidationPackageIdentifier> validationPackageIdentifiers()
	{
		if (validationPackages == null || validationPackages.isEmpty()
				|| validationPackages.stream().filter(Predicate.not(String::isBlank)).count() == 0)
			logger.warn(
					"Validation packages not specified, define at least one package via config parameter 'dev.dsf.validation.package' in the form 'name|version[, name|version]'");

		return validationPackages.stream().filter(Predicate.not(String::isBlank))
				.map(ValidationPackageIdentifier::fromString).toList();
	}
}
