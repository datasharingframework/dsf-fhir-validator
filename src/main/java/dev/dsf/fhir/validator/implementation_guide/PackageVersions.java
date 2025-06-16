package dev.dsf.fhir.validator.implementation_guide;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PackageVersions(@JsonProperty("_id") String id, @JsonProperty("name") String name,
		@JsonProperty("description") String description, @JsonProperty("dist-tags") PackageVersionsDistTags distTags,
		@JsonProperty("versions") Map<String, PackageVersionsVersions> versions)
{
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PackageVersionsDistTags(@JsonProperty("latest") String latest)
	{
		@JsonCreator
		public PackageVersionsDistTags(@JsonProperty("latest") String latest)
		{
			this.latest = latest;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PackageVersionsVersions(@JsonProperty("name") String name,
			@JsonProperty("description") String description, @JsonProperty("fhirVersion") String fhirVersion,
			@JsonProperty("version") String version, @JsonProperty("dist") PackageVersionsVersionsDist dist,
			@JsonProperty("url") String url, @JsonProperty("unlisted") String unlisted)
	{
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record PackageVersionsVersionsDist(@JsonProperty("shasum") String shasum,
				@JsonProperty("tarball") String tarball)
		{
			@JsonCreator
			public PackageVersionsVersionsDist(@JsonProperty("shasum") String shasum,
					@JsonProperty("tarball") String tarball)
			{
				this.shasum = shasum;
				this.tarball = tarball;
			}
		}

		@JsonCreator
		public PackageVersionsVersions(@JsonProperty("name") String name,
				@JsonProperty("description") String description, @JsonProperty("fhirVersion") String fhirVersion,
				@JsonProperty("version") String version, @JsonProperty("dist") PackageVersionsVersionsDist dist,
				@JsonProperty("url") String url, @JsonProperty("unlisted") String unlisted)
		{
			this.name = name;
			this.description = description;
			this.fhirVersion = fhirVersion;
			this.version = version;
			this.dist = dist;
			this.url = url;
			this.unlisted = unlisted;
		}
	}

	public PackageVersions(@JsonProperty("_id") String id, @JsonProperty("name") String name,
			@JsonProperty("description") String description,
			@JsonProperty("dist-tags") PackageVersionsDistTags distTags,
			@JsonProperty("versions") Map<String, PackageVersionsVersions> versions)
	{
		this.id = id;
		this.name = name;
		this.description = description;
		this.distTags = distTags;
		this.versions = versions;
	}
}
