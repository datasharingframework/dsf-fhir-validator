package dev.dsf.fhir.validator.value_set;

import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.terminologies.ValueSetExpander.ValueSetExpansionOutcome;

public interface ValueSetExpander
{
	ValueSetExpansionOutcome expand(ValueSet valueSet);
}
