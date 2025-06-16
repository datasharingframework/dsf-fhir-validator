# Data Sharing Framework (DSF) FHIR Validator

### Build
`mvn install`

### Run
By default the validator is configured to expand value-sets via https://ontoserver.mii-termserv.de and thus requires a client certificate to be configured.
The validator also needs to be configured with a list of implementation guide packages (name|version) to validate against.


Create `application.properties` file with the following content in your execution directory:

```
dev.dsf.validation.valueset.expansion.client.authentication.certificate: certificate.pem
dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key.password: private_key_password
dev.dsf.validation.valueset.expansion.client.authentication.certificate.private.key: privatekey.pem
dev.dsf.validation.package: \
  de.medizininformatikinitiative.kerndatensatz.laborbefund|2025.0.2, \
  de.medizininformatikinitiative.kerndatensatz.prozedur|2025.0.0, \
  de.medizininformatikinitiative.kerndatensatz.diagnose|2025.0.0
```

`java -jar target/dsf-fhir-validator.jar fhir-resource-to-validate.xml` (.json also supported)