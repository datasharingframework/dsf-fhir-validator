package dev.dsf.fhir.validator.value_set;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionParameterComponent;

public class VersionIncluder implements ValueSetModifier
{
	@Override
	public ValueSet modifyPostExpansion(ValueSet vsWithComposition, ValueSet vsWithExpansion)
	{
		if (vsWithExpansion == null)
			return null;
		if (!vsWithExpansion.hasExpansion())
			return vsWithExpansion;

		if (vsWithExpansion.getExpansion().getContains().stream().anyMatch(c -> c.getVersion() == null))
		{
			Map<String, String> versionsByUrl = vsWithExpansion.getExpansion().getParameter().stream()
					.filter(p -> "version".equals(p.getName()))
					.filter(ValueSetExpansionParameterComponent::hasValueUriType)
					.map(ValueSetExpansionParameterComponent::getValueUriType).map(UriType::getValue)
					.map(v -> v.split("\\|")).filter(v -> v.length == 2)
					.collect(Collectors.toMap(v -> v[0], v -> v[1], (v1, v2) -> null));

			vsWithExpansion.getExpansion().getContains().stream().filter(c -> c.getVersion() == null).forEach(c ->
			{
				String version = versionsByUrl.get(c.getVersion());
				if (version == null)
					version = getVersion(vsWithComposition, c.getSystem(), c.getCode());

				c.setVersion(version);
			});
		}

		return vsWithExpansion;
	}

	private String getVersion(ValueSet valueSet, String system, String code)
	{
		List<String> versions = valueSet.getCompose().getInclude().stream()
				.filter(c -> Objects.equals(system, c.getSystem())).map(c ->
				{
					if (c.getConcept().stream().anyMatch(co -> Objects.equals(code, co.getCode())))
						return c.getVersion();
					else
						return null;
				}).toList();

		return versions.size() == 1 ? versions.get(0) : null;
	}
}
