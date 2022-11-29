# IDS-Serializer
International Data Space Serializer 


# Build and Deploy the IDS-Serializer
1. Before you build prepare the revision of the build artifacts
   1. `revision` the revision of the multi project module
   2. `serializer.version` the revision of the serializer
   3. `provider.version` the revision of the validation-serialization-provider
2. Execute to build the jar files:
    - `mvn clean package -U -Drevision=<new revision number here> -Dserializer.version=<new revision serializer> -Dprovider.version=<new revision validation-serialization-provider>`
3. To deploy, make sure that the particular credentials for the repositories are provided in the `settings.xml` or via the maven property `-s <path to the settings.xml>`.
   To deploy the artifacts execute to eis-ids-public repository 
```shell 
mvn deploy -DaltDeploymentRepository=eis-public-repo::default::https://maven.iais.fraunhofer.de/artifactory/eis-ids-public -Drevision=<new revision number here> -Dserializer.version=<new revision serializer> -Dprovider.version=<new revision validation-serialization-provider>
```
for the step 3. you need the credentials for the repository. 