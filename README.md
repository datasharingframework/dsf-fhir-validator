# Data Sharing Framework (DSF) FHIR Validator

### Build
`mvn install`

### Run
By default the validator is configured to expand value-sets via https://ontoserver.mii-termserv.de and thus requires a client certificate to be configured.

Create `application.properties` file with the following content in your execution directory:

```
dev.dsf.validation.valueset.expansion.client.authentication.certificate:certificate.pem
dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key.password:private_key_password
dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key:privatekey.pem
```

`java -jar target/dsf-fhir-validator.jar fhir-resource-to-validate.xml` (.json supported also)