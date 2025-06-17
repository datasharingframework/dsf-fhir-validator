package dev.dsf.fhir.validator.implementation_guide;

public record ValidationPackageIdentifier(String name, String version)
{
	public static ValidationPackageIdentifier fromString(String nameAndVersion)
	{
		String[] split = nameAndVersion.split("\\|");

		if (split.length != 2)
			throw new IllegalArgumentException("Validation package not specified as 'name|version'");

		return new ValidationPackageIdentifier(split[0], split[1]);
	}

	@Override
	public String toString()
	{
		return name + "|" + version;
	}
}