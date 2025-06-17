package dev.dsf.fhir.validator.client;

import java.security.KeyStore;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.logging.LoggingFeature.Verbosity;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;

public class TerminologyServerClientJersey implements TerminologyServerClient
{
	private static final Logger logger = LoggerFactory.getLogger(TerminologyServerClientJersey.class);
	private static final java.util.logging.Logger requestDebugLogger;
	static
	{
		requestDebugLogger = java.util.logging.Logger.getLogger(TerminologyServerClientJersey.class.getName());
		requestDebugLogger.setLevel(Level.INFO);
	}

	private final Client client;
	private final String baseUrl;

	public TerminologyServerClientJersey(String baseUrl, KeyStore trustStore, KeyStore keyStore,
			char[] keyStorePassword, String proxySchemeHostPort, String proxyUsername, char[] proxyPassword,
			int connectTimeout, int readTimeout, boolean logRequests, ObjectMapper objectMapper,
			FhirContext fhirContext)
	{
		SSLContext sslContext = null;
		if (trustStore != null && keyStore == null && keyStorePassword == null)
			sslContext = SslConfigurator.newInstance().trustStore(trustStore).createSSLContext();
		else if (trustStore == null && keyStore != null && keyStorePassword != null)
			sslContext = SslConfigurator.newInstance().keyStore(keyStore).keyStorePassword(keyStorePassword)
					.createSSLContext();
		else if (trustStore != null && keyStore != null && keyStorePassword != null)
			sslContext = SslConfigurator.newInstance().trustStore(trustStore).keyStore(keyStore)
					.keyStorePassword(keyStorePassword).createSSLContext();

		ClientBuilder builder = ClientBuilder.newBuilder();

		if (sslContext != null)
			builder = builder.sslContext(sslContext);

		ClientConfig config = new ClientConfig();
		config.connectorProvider(new ApacheConnectorProvider());
		config.property(ClientProperties.PROXY_URI, proxySchemeHostPort);
		config.property(ClientProperties.PROXY_USERNAME, proxyUsername);
		config.property(ClientProperties.PROXY_PASSWORD, proxyPassword == null ? null : String.valueOf(proxyPassword));
		builder = builder.withConfig(config);

		builder = builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS).connectTimeout(connectTimeout,
				TimeUnit.MILLISECONDS);

		if (objectMapper != null)
		{
			JacksonJaxbJsonProvider p = new JacksonJaxbJsonProvider(JacksonJsonProvider.BASIC_ANNOTATIONS);
			p.setMapper(objectMapper);
			builder = builder.register(p);
		}

		builder = builder.register(new FhirAdapter(fhirContext));

		if (logRequests)
		{
			builder = builder.register(new LoggingFeature(requestDebugLogger, Level.INFO, Verbosity.PAYLOAD_ANY,
					LoggingFeature.DEFAULT_MAX_ENTITY_SIZE));
		}

		client = builder.build();

		this.baseUrl = baseUrl;
	}

	private WebTarget getResource()
	{
		return client.target(baseUrl);
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

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("valueSet").setResource(valueSet);

		return getResource().path("ValueSet").path("$expand").request(Constants.CT_FHIR_JSON_NEW)
				.post(Entity.entity(parameters, Constants.CT_FHIR_JSON_NEW), ValueSet.class);
	}

	@Override
	public ValidationResult validate(Coding coding) throws WebApplicationException
	{
		Objects.requireNonNull(coding, "coding");

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("coding").setValue(coding);

		Parameters result = getResource().path("CodeSystem").path("$validate-code").request(Constants.CT_FHIR_JSON_NEW)
				.post(Entity.entity(parameters, Constants.CT_FHIR_JSON_NEW), Parameters.class);

		return new ValidationResult(result.getParameterBool("result"),
				result.getParameter("message") != null
						&& result.getParameter("message").getValue() instanceof StringType s ? s.getValue() : null,
				result.getParameter("issues") != null
						&& result.getParameter("issues").getResource() instanceof OperationOutcome o ? o : null);
	}

	@Override
	public CapabilityStatement getMetadata() throws WebApplicationException
	{
		return getResource().path("metadata").request(Constants.CT_FHIR_JSON_NEW).get(CapabilityStatement.class);
	}

	@Override
	public List<UrlAndVersion> getSupportedCodeSystemVersion(String url) throws WebApplicationException
	{
		Bundle resultBundle = getResource().path("CodeSystem").queryParam("url", url).queryParam("_summary", "true")
				.request(Constants.CT_FHIR_JSON_NEW).get(Bundle.class);

		return resultBundle.getEntry().stream().filter(BundleEntryComponent::hasResource)
				.map(BundleEntryComponent::getResource).filter(r -> r instanceof CodeSystem).map(r -> (CodeSystem) r)
				.filter(cs -> cs.hasVersionElement() && cs.getVersionElement().hasValue())
				.map(UrlAndVersion::fromCodeSystem).distinct().toList();
	}
}
