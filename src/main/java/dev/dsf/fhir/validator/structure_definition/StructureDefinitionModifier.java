package dev.dsf.fhir.validator.structure_definition;

import org.hl7.fhir.r4.model.StructureDefinition;

@FunctionalInterface
public interface StructureDefinitionModifier
{
	StructureDefinition modify(StructureDefinition sd);
}
