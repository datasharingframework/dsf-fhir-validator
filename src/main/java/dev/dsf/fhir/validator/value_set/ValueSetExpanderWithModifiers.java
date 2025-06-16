package dev.dsf.fhir.validator.value_set;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.springframework.beans.factory.InitializingBean;

public class ValueSetExpanderWithModifiers implements ValueSetExpander, InitializingBean
{
	private final ValueSetExpander delegate;

	private final List<ValueSetModifier> valueSetModifiers = new ArrayList<>();

	public ValueSetExpanderWithModifiers(ValueSetExpander delegate,
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
	public ValueSetExpansionOutcome expand(ValueSet valueSet)
	{
		if (valueSet == null)
			return null;

		for (ValueSetModifier modifier : valueSetModifiers)
			valueSet = modifier.modifyPreExpansion(valueSet);

		ValueSetExpansionOutcome outcome = delegate.expand(valueSet);

		if (outcome.isOk())
		{
			for (ValueSetModifier modifier : valueSetModifiers)
				modifier.modifyPostExpansion(valueSet, outcome.getValueset());
		}

		return outcome;
	}
}
