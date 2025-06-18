package dev.dsf.fhir.validator.value_set;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import dev.dsf.fhir.validator.client.TerminologyServerClient;
import jakarta.ws.rs.WebApplicationException;

public class ValueSetExpansionClientStarVersion implements TerminologyServerClient, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValueSetExpansionClientStarVersion.class);

	private final TerminologyServerClient delegate;

	public ValueSetExpansionClientStarVersion(TerminologyServerClient delegate)
	{
		this.delegate = delegate;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public ValueSet expand(ValueSet valueSet) throws WebApplicationException
	{
		Objects.requireNonNull(valueSet, "valueSet");

		Optional<String> starSystem = valueSet.getCompose().getInclude().size() != 1 ? Optional.empty()
				: valueSet.getCompose().getInclude().stream()
						.filter(i -> i.hasVersionElement() && i.getVersionElement().hasValue()
								&& "*".equals(i.getVersion()) && i.hasSystemElement()
								&& i.getSystemElement().hasValue())
						.map(ConceptSetComponent::getSystem).findFirst();

		return starSystem.map(s ->
		{
			getSupportedCodeSystemVersion(s).stream().sorted(Comparator.comparing(UrlAndVersion::version))
					.map(expandForVersion(valueSet.copy()))
					.forEach(v -> valueSet.getExpansion().getContains().addAll(v.getExpansion().getContains()));

			return valueSet;

		}).orElseGet(() -> delegate.expand(valueSet));
	}

	private Function<UrlAndVersion, ValueSet> expandForVersion(ValueSet valueSet)
	{
		return urlAndVersion ->
		{
			logger.debug("Expanding ValueSet {}|{} with version wildcard include for CodeSystem {}|{}",
					valueSet.getUrl(), valueSet.getVersion(), urlAndVersion.url(), urlAndVersion.version());

			valueSet.getCompose().setInclude(null);
			valueSet.getCompose().addInclude().setSystem(urlAndVersion.url()).setVersion(urlAndVersion.version());

			ValueSet expanded = delegate.expand(valueSet);
			expanded.getExpansion().getContains().stream().forEach(c -> c.setVersion(urlAndVersion.version()));
			return expanded;
		};
	}

	@Override
	public CapabilityStatement getMetadata() throws WebApplicationException
	{
		return delegate.getMetadata();
	}

	@Override
	public ValidationResult validate(Coding coding) throws WebApplicationException
	{
		Objects.requireNonNull(coding, "coding");

		return delegate.validate(coding);
	}

	@Override
	public List<UrlAndVersion> getSupportedCodeSystemVersion(String url) throws WebApplicationException
	{
		Objects.requireNonNull(url, "url");

		return delegate.getSupportedCodeSystemVersion(url);
	}
}
