# IDS Information Model Validation and Serialization Provider

This is an implementation of service providers for the bean validation and RDF serialization interfaces of the
[Information Model Java library](https://maven.iais.fraunhofer.de/artifactory/eis-ids-snapshot/de/fraunhofer/iais/eis/ids/infomodel/).
More specifically, it: 

* validates instances against the javax.validation annotations in the library's Java beans, 
* implements RDF serialization methods of the Java beans, and 
* implements the RDF deserialization via the VocabUtils class of the library.  

## Background

The Information Model Java library implements Bean validation and RDF serialization as [Service Provider Interfaces](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html).
The code in this repository provides implementations of these interfaces.


## Usage

The code is deployed on the [Maven repository](https://maven.iais.fraunhofer.de/artifactory/eis-ids-snapshot/de/fraunhofer/iais/eis/ids/infomodel)
of Fraunhofer IAIS and can be included by the following dependency:

 ```
 <dependency>
    <groupId>de.fraunhofer.iais.eis.ids.infomodel</groupId>
    <artifactId>validate-serialize</artifactId>
    <version>3.0.0</version>
</dependency>
```

## Build and Deploy IDS Information Model Validation and Serialization Provider
1. Execute to build the jar file:
    - `mvn clean package -U -Drevision=<new revision number here>`
2. To deploy, make sure that the particular credentials for the repositories are provided in the `settings.xml` or via the maven property `-s <path to the settings.xml>`.
   To deploy the artifacts execute:
   - to eis-ids-public repository `mvn deploy -DaltDeploymentRepository=eis-public-repo::default::https://maven.iais.fraunhofer.de/artifactory/eis-ids-public -Drevision=<new revision number here>`
   - to eis-ids-snapshot repository `mvn deploy -DaltDeploymentRepository=eis-snapshot-repo::default::https://maven.iais.fraunhofer.de/artifactory/eis-ids-snapshot  -Drevision=<new revision number here>`
   - to eis-ids-release repository `mvn deploy -DaltDeploymentRepository=eis-release-repo::default::http://maven.iais.fraunhofer.de/artifactory/eis-ids-release  -Drevision=<new revision number here>`

## Status

Currently, the supported version of the [IDS Infomodel](https://github.com/IndustrialDataSpace/InformationModel) is 3.0.0 (development branch).

## Contributors

* Christian Mader (Fraunhofer IAIS)
* Sebastian Bader (Fraunhofer IAIS)



