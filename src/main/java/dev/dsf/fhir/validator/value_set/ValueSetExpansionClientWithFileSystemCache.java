package dev.dsf.fhir.validator.value_set;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.context.FhirContext;
import dev.dsf.fhir.validator.cache.AbstractFhirResourceFileSystemCache;
import dev.dsf.fhir.validator.cache.AbstractFileSystemCache;
import dev.dsf.fhir.validator.client.TerminologyServerClient;
import dev.dsf.fhir.validator.implementation_guide.ValidationPackageClientWithFileSystemCache;
import jakarta.ws.rs.WebApplicationException;

public class ValueSetExpansionClientWithFileSystemCache extends AbstractFhirResourceFileSystemCache<ValueSet, ValueSet>
		implements TerminologyServerClient, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValidationPackageClientWithFileSystemCache.class);

	private final TerminologyServerClient delegate;
	private final boolean cacheDraftResources;

	/**
	 * For JSON content with gzip compression using the <code>.json.xz</code> file name suffix.
	 *
	 * @param cacheFolder
	 *            not <code>null</code>
	 * @param fhirContext
	 *            not <code>null</code>
	 * @param delegate
	 *            not <code>null</code>
	 * @param cacheDraftResources
	 * @see AbstractFileSystemCache#FILENAME_SUFFIX
	 * @see AbstractFileSystemCache#OUT_COMPRESSOR_FACTORY
	 * @see AbstractFileSystemCache#IN_COMPRESSOR_FACTORY
	 */
	public ValueSetExpansionClientWithFileSystemCache(Path cacheFolder, FhirContext fhirContext,
			TerminologyServerClient delegate, boolean cacheDraftResources)
	{
		super(cacheFolder, ValueSet.class, fhirContext);

		this.delegate = delegate;
		this.cacheDraftResources = cacheDraftResources;
	}

	public ValueSetExpansionClientWithFileSystemCache(Path cacheFolder, String fileNameSuffix,
			FunctionWithIoException<OutputStream, OutputStream> outCompressorFactory,
			FunctionWithIoException<InputStream, InputStream> inCompressorFactory, FhirContext fhirContext,
			TerminologyServerClient delegate, boolean cacheDraftResources)
	{
		super(cacheFolder, fileNameSuffix, outCompressorFactory, inCompressorFactory, ValueSet.class, fhirContext);

		this.delegate = delegate;
		this.cacheDraftResources = cacheDraftResources;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public ValueSet expand(ValueSet valueSet) throws WebApplicationException
	{
		Objects.requireNonNull(valueSet, "valueSet");

		if (valueSet.hasExpansion())
		{
			logger.debug("ValueSet {}|{} already expanded", valueSet.getUrl(), valueSet.getVersion());
			return valueSet;
		}

		Objects.requireNonNull(valueSet.getUrl(), "valueSet.url");
		Objects.requireNonNull(valueSet.getVersion(), "valueSet.version");

		ValueSet read = readResourceFromCache(valueSet.getUrl(), valueSet.getVersion(), Function.identity());

		if (read != null)
			return read;
		else
			return expandAndWriteToCache(valueSet);
	}

	private ValueSet expandAndWriteToCache(ValueSet valueSet)
	{
		ValueSet expanded = delegate.expand(valueSet);

		if (PublicationStatus.DRAFT.equals(expanded.getStatus()) && !cacheDraftResources)
		{
			logger.info("Not writing expanded ValueSet {}|{} with status {} to cache", expanded.getUrl(),
					expanded.getVersion(), expanded.getStatus());
			return expanded;
		}
		else
			return writeRsourceToCache(expanded, Function.identity(), ValueSet::getUrl, ValueSet::getVersion);
	}

	@Override
	public CapabilityStatement getMetadata() throws WebApplicationException
	{
		return delegate.getMetadata();
	}

	@Override
	public ValidationResult validate(Coding coding) throws WebApplicationException
	{
		return delegate.validate(coding);
	}
}
