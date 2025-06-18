package dev.dsf.fhir.validator.value_set;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.InitializingBean;

import dev.dsf.fhir.validator.client.TerminologyServerClient;
import jakarta.ws.rs.WebApplicationException;

public class ValueSetExpansionClientWithModifiers implements TerminologyServerClient, InitializingBean
{
	private final TerminologyServerClient delegate;
	private final List<ValueSetModifier> valueSetModifiers = new ArrayList<>();

	public ValueSetExpansionClientWithModifiers(TerminologyServerClient delegate,
			Collection<? extends ValueSetModifier> valueSetModifiers)
	{
		this.delegate = delegate;

		if (valueSetModifiers != null)
			this.valueSetModifiers.addAll(valueSetModifiers);
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

		for (ValueSetModifier modifier : valueSetModifiers)
			valueSet = modifier.modifyPreExpansion(valueSet);

		ValueSet expandedValueSet = delegate.expand(valueSet);

		for (ValueSetModifier modifier : valueSetModifiers)
			expandedValueSet = modifier.modifyPostExpansion(valueSet, expandedValueSet);

		return expandedValueSet;
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
