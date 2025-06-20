package dev.dsf.fhir.validator.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.function.Supplier;

import org.hl7.fhir.r4.model.BaseResource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Consumes({ Constants.CT_FHIR_XML_NEW, Constants.CT_FHIR_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
		Constants.CT_FHIR_JSON_NEW, Constants.CT_FHIR_JSON, MediaType.APPLICATION_JSON })
@Produces({ Constants.CT_FHIR_XML_NEW, Constants.CT_FHIR_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
		Constants.CT_FHIR_JSON_NEW, Constants.CT_FHIR_JSON, MediaType.APPLICATION_JSON })
public class FhirAdapter implements MessageBodyReader<BaseResource>, MessageBodyWriter<BaseResource>
{
	private final FhirContext fhirContext;

	public FhirAdapter(FhirContext fhirContext)
	{
		this.fhirContext = fhirContext;
	}

	private IParser getParser(Supplier<IParser> parserFactor)
	{
		/* Parsers are not guaranteed to be thread safe */
		IParser p = parserFactor.get();
		p.setStripVersionsFromReferences(false);
		p.setOverrideResourceIdWithBundleEntryFullUrl(false);
		return p;
	}

	private IParser getParser(MediaType mediaType)
	{
		return switch (mediaType.getType() + "/" + mediaType.getSubtype())
		{
			case Constants.CT_FHIR_XML_NEW, Constants.CT_FHIR_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML ->
				getParser(fhirContext::newXmlParser);
			case Constants.CT_FHIR_JSON_NEW, Constants.CT_FHIR_JSON, MediaType.APPLICATION_JSON ->
				getParser(fhirContext::newJsonParser);
			default -> throw new IllegalStateException("MediaType " + mediaType.toString() + " not supported");
		};
	}

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
	{
		return type != null && BaseResource.class.isAssignableFrom(type);
	}

	@Override
	public void writeTo(BaseResource t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
			throws IOException, WebApplicationException
	{
		getParser(mediaType).encodeResourceToWriter(t, new OutputStreamWriter(entityStream));
	}

	@Override
	public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
	{
		return type != null && BaseResource.class.isAssignableFrom(type);
	}

	@Override
	public BaseResource readFrom(Class<BaseResource> type, Type genericType, Annotation[] annotations,
			MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
			throws IOException, WebApplicationException
	{
		return getParser(mediaType).parseResource(type, new InputStreamReader(entityStream));
	}
}
