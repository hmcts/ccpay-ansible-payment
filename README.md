## HMCTS Payment Gateway
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=uk.gov.hmcts.reform.payment%3Apayment-app&metric=alert_status)](https://sonarcloud.io/dashboard?id=uk.gov.hmcts.reform.payment%3Apayment-app)
[![Build Status](https://travis-ci.org/hmcts/ccpay-payment-app.svg?branch=master)](https://travis-ci.org/hmcts/ccpay-payment-app)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/0cb10a161dc24d0092470cda7c304c87)](https://app.codacy.com/app/HMCTS/ccpay-payment-app)
[![codecov](https://codecov.io/gh/hmcts/ccpay-payment-app/branch/master/graph/badge.svg)](https://codecov.io/gh/hmcts/ccpay-payment-app)

HMCTS Payment Gateway is a small wrapper around GOV.UK Pay service adding some basic user/service authorization, 
enforcing useful payment reference structure and collecting data across multiple GOV.UK Pay accounts that will allow 
better financial reporting and reconciliation.   
 
### Integration prerequisites


For a successful integration with HMCTS Payment Gateway you will require the following:
* **GOV.UK Pay API key**. Before you start, you should get a dedicated GOV.UK Pay account created for you. Once it is done, 
you should use GOV.UK Pay admin console to create your API key(-s) and provide them to us.
* **IDAM**. All requests to Payment Gateway require a valid user JWT token to be passed in "Authorization" header. 
Please make sure your application is integrated with IDAM before you start.  
* **service-auth-provider**. All requests to Payment Gateway require a valid service JWT token to be passed in 
"ServiceAuthorization" header. Please make sure your application is registered in service-auth-provider-app and you are 
able to acquire service JWT tokens.

### Integration GOTCHAs

* **Stale Payment Status**. Neither HMCTS Payment Gateway, nor GOV.UK Pay support "PUSH" notifications for payment status update. 
Therefore, a situation where a user has made a payment but his redirection back to the "return" url failed (e.g. due to interrupted 
internet connection), would lead to a payment status not being reflected in your application until you query its status again.
You should take this into consideration and if necessary implement some background job for refreshing payment status.
* **Access authorization**. Payment gateway implements a simple url based authorization rule. User with id 999, will only be granted 
access to urls /users/999/payments/\*, any request to /users/{OTHER_ID}/payments/\* will result in 403.
* **Refunds**. Although, both HMCTS Payment Gateway and GOV.UK Pay implement refund endpoints, they **WILL NOT WORK** due to limitations
of MoJ financial arrangements  & back-office systems.

### Building
The project uses [Gradle](https://gradle.org) as a build tool but you don't have install it locally since there is a
`./gradlew` wrapper script.  

To build project please execute the following command:

```bash
$ ./gradlew build
```
### Tests

This project uses [TestContainers](https://www.testcontainers.org/usage/database_containers.html#jdbc-url) for database support.
Docker must be installed on the machine you are running tests on and docker environment should have more than 2GB free disk space. 

Windows users may need to enable this [setting](https://github.com/testcontainers/testcontainers-java/issues/350)
Linux users may need to add their current user to the docker group:
```bash
$ sudo usermod -aG docker $USER
```

To run all unit tests please execute the following command:

```bash
$ ./gradlew test
```

### Endpoints

* POST /users/{userId}/payments - create payment
* GET /users/{userId}/payments/{paymentId} - get payment
* POST /users/{userId}/payments/{paymentId}/cancel - cancel payment

Please refer to Swagger UI and Gov.UK Pay for more details.


### Useful Links
* https://gds-payments.gelato.io/docs/versions/1.0.0/resources/general
* https://github.com/hmcts/ccpay-reference-app
* https://github.com/hmcts/ccpay-reference-web

## How to generate Liquibase yaml file
Liquibase is used to update the database changes. Perform following steps to create and update the new yaml file. 

1. cd model
2. run command $mvn liquibase:diff
3. this will generate a new yaml file e.g. api/src/main/resources/db/changelog/db.changelog-0.0.5.yaml
5. Add this file to pom.xml in diffChangeLogFile section under configuration.
6. Add this file to db.changelog-master.xml's diff files list

#### Environment variables

The following environment variables are required:

- `APPINSIGHTS_INSTRUMENTATIONKEY`, app insights key to send telemetry events.
