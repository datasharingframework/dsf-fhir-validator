package dev.dsf.fhir.validator.service;

import org.hl7.fhir.r4.model.Resource;

import ca.uhn.fhir.validation.ValidationResult;

public interface ResourceValidator
{
	ValidationResult validate(Resource resource);
}