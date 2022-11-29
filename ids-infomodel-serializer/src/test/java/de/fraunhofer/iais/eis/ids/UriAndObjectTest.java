package de.fraunhofer.iais.eis.ids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

public class UriAndObjectTest {

    Logger logger = LoggerFactory.getLogger(UriAndObjectTest.class);


    @Test
    public void UriOrModelClassBaseConnectorCorrectTranslationTest() throws IOException {
        // In single fields, XXXAsObject-fields always have priority, regardless of the order in which they are added.
        BaseConnector baseConnector = new BaseConnectorBuilder()
                ._curatorAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/1"))
                ._curatorAsObject_(new ParticipantBuilder()
                        ._version_("1")
                        ._legalForm_("Very legal")
                        .build())
                ._hasAgent_(new ArrayList<>(Arrays.asList(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))))
                ._maintainerAsObject_(new ParticipantBuilder()
                        ._version_("2")
                        ._legalForm_("illegal")
                        .build())
                ._maintainerAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()
                        ._accessURL_(URI.create("http://example.com/endpoint/uriormodelclasscorrecttranslation/1"))
                        .build()
                )
                ._inboundModelVersion_("4.4.4")
                ._outboundModelVersion_("4.4.4")
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                .build();
        String baseConnectorAsString = new Serializer().serialize(baseConnector);
        logger.info(baseConnectorAsString);

        Assert.assertTrue(baseConnectorAsString.contains("Very legal"));
        Assert.assertTrue(baseConnectorAsString.contains("illegal"));
        // Depends on the InformationModel version!
        //Assert.assertTrue(baseConnectorAsString.contains("http://example.com/participant/uriormodelclasscorrecttranslation/1"));
        Assert.assertFalse(baseConnectorAsString.contains("http://example.com/participant/uriormodelclasscorrecttranslation/1"));

        BaseConnector recreated = new Serializer().deserialize(baseConnectorAsString, BaseConnector.class);
        String recreatedBaseConnectorAsString = new Serializer().serialize(recreated);

        //logger.info(recreatedBaseConnectorAsString);

        Assert.assertTrue(recreatedBaseConnectorAsString.contains("Very legal"));
        Assert.assertTrue(recreatedBaseConnectorAsString.contains("illegal"));
        //Assert.assertFalse(recreatedBaseConnectorAsString.contains("http://example.com/participant/uriormodelclasscorrecttranslation/1"));

        Assert.assertEquals(SecurityProfile.BASE_SECURITY_PROFILE.getId(), recreated.getSecurityProfile().getId());
        Assert.assertEquals(SecurityProfile.BASE_SECURITY_PROFILE, recreated.getSecurityProfile());
    }

    @Test
    public void ParseWithComplexClassForEnum() throws IOException {
        String baseConnectorAsString = "{\n" +
                "  \"@context\" : {\n" +
                "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
                "    \"idsc\" : \"https://w3id.org/idsa/code/\"\n" +
                "  },\n" +
                "  \"@type\" : \"ids:BaseConnector\",\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/baseConnector/d20926b5-a884-47e5-9929-b235d8c1471f\",\n" +
                "  \"ids:securityProfile\" : {\n" +
                "    \"@id\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
                "    \"@type\" : \"ids:SecurityProfile\",\n" +
                "    \"ids:securityGuarantee\" : {\"@type\": \"ids:SecurityGuarantee\", \"@id\": \"http://example.org/sg123\"}\n" +
                "  },\n" +
                "  \"ids:hasAgent\" : [ {\n" +
                "    \"@id\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/2\"\n" +
                "  } ],\n" +
                "  \"ids:curator\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/02dcd764-e1f0-423c-b745-81a33349f5e5\",\n" +
                "    \"ids:version\" : \"1\",\n" +
                "    \"ids:legalForm\" : \"Very legal\"\n" +
                "  },\n" +
                "  \"ids:maintainer\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/cd5780d0-abee-465c-877b-70e3030b85fa\",\n" +
                "    \"ids:version\" : \"2\",\n" +
                "    \"ids:legalForm\" : \"illegal\"\n" +
                "  },\n" +
                "  \"ids:outboundModelVersion\" : \"4.4.4\",\n" +
                "  \"ids:hasDefaultEndpoint\" : {\n" +
                "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/16bb26e0-5539-4045-a05b-bf07ce88f718\",\n" +
                "    \"ids:accessURL\" : {\n" +
                "      \"@id\" : \"http://example.com/endpoint/uriormodelclasscorrecttranslation/1\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"ids:inboundModelVersion\" : [ \"4.4.4\" ]\n" +
                "}";
        BaseConnector recreated = new Serializer().deserialize(baseConnectorAsString, BaseConnector.class);
        logger.info(new Serializer().serialize(recreated));
        Assert.assertNotEquals(recreated.getSecurityProfile(), SecurityProfile.BASE_SECURITY_PROFILE);
    }

    @Test
    public void ParseWithTypeForInterfaceInstance() throws IOException {
        String baseConnectorAsString = "{\n" +
                "  \"@context\" : {\n" +
                "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
                "    \"idsc\" : \"https://w3id.org/idsa/code/\"\n" +
                "  },\n" +
                "  \"@type\" : \"ids:BaseConnector\",\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/baseConnector/d20926b5-a884-47e5-9929-b235d8c1471f\",\n" +
                "  \"ids:securityProfile\" : {\n" +
                "    \"@id\" : \"idsc:BASE_SECURITY_PROFILE\",\n" +
                "    \"@type\" : \"ids:SecurityProfile\"\n" +
                "  },\n" +
                "  \"ids:hasAgent\" : [ {\n" +
                "    \"@id\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/2\"\n" +
                "  } ],\n" +
                "  \"ids:curator\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/02dcd764-e1f0-423c-b745-81a33349f5e5\",\n" +
                "    \"ids:version\" : \"1\",\n" +
                "    \"ids:legalForm\" : \"Very legal\"\n" +
                "  },\n" +
                "  \"ids:maintainer\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/cd5780d0-abee-465c-877b-70e3030b85fa\",\n" +
                "    \"ids:version\" : \"2\",\n" +
                "    \"ids:legalForm\" : \"illegal\"\n" +
                "  },\n" +
                "  \"ids:outboundModelVersion\" : \"4.4.4\",\n" +
                "  \"ids:hasDefaultEndpoint\" : {\n" +
                "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/16bb26e0-5539-4045-a05b-bf07ce88f718\",\n" +
                "    \"ids:accessURL\" : {\n" +
                "      \"@id\" : \"http://example.com/endpoint/uriormodelclasscorrecttranslation/1\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"ids:inboundModelVersion\" : [ \"4.4.4\" ]\n" +
                "}";
        BaseConnector recreated = new Serializer().deserialize(baseConnectorAsString, BaseConnector.class);
        logger.info(new Serializer().serialize(recreated));
        Assert.assertEquals(recreated.getSecurityProfile(), SecurityProfile.BASE_SECURITY_PROFILE);
    }

    @Test
    public void ParseWithoutTypeForInterfaceInstance() throws IOException {
        String baseConnectorAsString = "{\n" +
                "  \"@context\" : {\n" +
                "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
                "    \"idsc\" : \"https://w3id.org/idsa/code/\"\n" +
                "  },\n" +
                "  \"@type\" : \"ids:BaseConnector\",\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/baseConnector/d20926b5-a884-47e5-9929-b235d8c1471f\",\n" +
                "  \"ids:securityProfile\" : {\n" +
                "    \"@id\" : \"idsc:BASE_SECURITY_PROFILE\"\n" +
                "  },\n" +
                "  \"ids:hasAgent\" : [ {\n" +
                "    \"@id\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/2\"\n" +
                "  } ],\n" +
                "  \"ids:curator\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/02dcd764-e1f0-423c-b745-81a33349f5e5\",\n" +
                "    \"ids:version\" : \"1\",\n" +
                "    \"ids:legalForm\" : \"Very legal\"\n" +
                "  },\n" +
                "  \"ids:maintainer\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/cd5780d0-abee-465c-877b-70e3030b85fa\",\n" +
                "    \"ids:version\" : \"2\",\n" +
                "    \"ids:legalForm\" : \"illegal\"\n" +
                "  },\n" +
                "  \"ids:outboundModelVersion\" : \"4.4.4\",\n" +
                "  \"ids:hasDefaultEndpoint\" : {\n" +
                "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/16bb26e0-5539-4045-a05b-bf07ce88f718\",\n" +
                "    \"ids:accessURL\" : {\n" +
                "      \"@id\" : \"http://example.com/endpoint/uriormodelclasscorrecttranslation/1\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"ids:inboundModelVersion\" : [ \"4.4.4\" ]\n" +
                "}";
        BaseConnector recreated = new Serializer().deserialize(baseConnectorAsString, BaseConnector.class);
        logger.info(new Serializer().serialize(recreated));
        Assert.assertEquals(recreated.getSecurityProfile(), SecurityProfile.BASE_SECURITY_PROFILE);
    }

    @Test
    public void UriOrModelClassBaseConnectorCustomSecurityProfile() throws IOException {
        SecurityProfile customSecurityProfile = new SecurityProfileBuilder().build();
        BaseConnector baseConnector = new BaseConnectorBuilder()
                ._curatorAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/1"))
                ._curatorAsObject_(new ParticipantBuilder()
                        ._version_("1")
                        ._legalForm_("Very legal")
                        .build())
                ._hasAgent_(new ArrayList<>(Arrays.asList(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))))
                ._maintainerAsObject_(new ParticipantBuilder()
                        ._version_("2")
                        ._legalForm_("illegal")
                        .build())
                ._maintainerAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()
                        ._accessURL_(URI.create("http://example.com/endpoint/uriormodelclasscorrecttranslation/1"))
                        .build()
                )
                ._inboundModelVersion_("4.4.4")
                ._outboundModelVersion_("4.4.4")
                ._securityProfile_(customSecurityProfile)
                .build();
        String baseConnectorAsString = new Serializer().serialize(baseConnector);
        logger.info(baseConnectorAsString);

        Assert.assertTrue(baseConnectorAsString.contains("Very legal"));
        Assert.assertTrue(baseConnectorAsString.contains("illegal"));
        // Assertion below is only true, if for the ids:curator property there is a maxCount of 1, which currently is not the case
        //Assert.assertFalse(baseConnectorAsString.contains("http://example.com/participant/uriormodelclasscorrecttranslation/1"));

        BaseConnector recreated = new Serializer().deserialize(baseConnectorAsString, BaseConnector.class);
        String recreatedBaseConnectorAsString = new Serializer().serialize(recreated);

        //logger.info(recreatedBaseConnectorAsString);

        Assert.assertTrue(recreatedBaseConnectorAsString.contains("Very legal"));
        Assert.assertTrue(recreatedBaseConnectorAsString.contains("illegal"));
        // Assertion below is only true, if for the ids:curator property there is a maxCount of 1, which currently is not the case
        //Assert.assertFalse(recreatedBaseConnectorAsString.contains("http://example.com/participant/uriormodelclasscorrecttranslation/1"));

        Assert.assertEquals(customSecurityProfile.getId(), recreated.getSecurityProfile().getId());
        Assert.assertEquals(customSecurityProfile, recreated.getSecurityProfile());
    }

    @Test
    public void UriOrModelClassResourceCatalogTranslationTest() throws IOException {
        // A property without maxCount=1 can have Objects and URIs!
        ResourceCatalog resourceCatalog = new ResourceCatalogBuilder()
                ._offeredResourceAsObject_(new ArrayList<>(Arrays.asList(new ResourceBuilder()
                        ._version_("Resource V1")
                        .build()
                )))
                ._offeredResourceAsUri_(new ArrayList<>(Arrays.asList(
                        URI.create("http://example.com/resource/uriormodelclasscorrecttranslation/1"),
                        URI.create("http://example.com/resource/uriormodelclasscorrecttranslation/2")))
                )
                ._requestedResourceAsObject_(new ResourceBuilder()
                        ._version_("Resource V2")
                        .build()
                )
                ._requestedResourceAsUri_(URI.create("http://example.com/resource/uriormodelclasscorrecttranslation/3"))
                ._requestedResourceAsUri_(URI.create("http://example.com/resource/uriormodelclasscorrecttranslation/4"))
                .build();
        String resourceCatalogAsString = new Serializer().serialize(resourceCatalog);
        logger.info(resourceCatalogAsString);

        Assert.assertTrue(resourceCatalogAsString.contains("Resource V1"));
        Assert.assertTrue(resourceCatalogAsString.contains("Resource V2"));
        Assert.assertTrue(resourceCatalogAsString.contains("http://example.com/resource/uriormodelclasscorrecttranslation/1"));
        Assert.assertTrue(resourceCatalogAsString.contains("http://example.com/resource/uriormodelclasscorrecttranslation/4"));

        ResourceCatalog recreated = new Serializer().deserialize(resourceCatalogAsString, ResourceCatalog.class);
        String recreatedResourceCatalogAsString = new Serializer().serialize(recreated);

        Assert.assertTrue(recreatedResourceCatalogAsString.contains("Resource V1"));
        Assert.assertTrue(recreatedResourceCatalogAsString.contains("Resource V2"));
        Assert.assertTrue(recreatedResourceCatalogAsString.contains("http://example.com/resource/uriormodelclasscorrecttranslation/1"));
        Assert.assertTrue(recreatedResourceCatalogAsString.contains("http://example.com/resource/uriormodelclasscorrecttranslation/4"));
    }

    @Test
    public void EnumSerializationWithJackson() throws JsonProcessingException {
        BaseConnector baseConnector = new BaseConnectorBuilder()
                ._curatorAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/1"))
                ._curatorAsObject_(new ParticipantBuilder()
                        ._version_("1")
                        ._legalForm_("Very legal")
                        .build())
                ._hasAgent_(new ArrayList<>(Arrays.asList(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))))
                ._maintainerAsObject_(new ParticipantBuilder()
                        ._version_("2")
                        ._legalForm_("illegal")
                        .build())
                ._maintainerAsUri_(URI.create("http://example.com/participant/uriormodelclasscorrecttranslation/2"))
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()
                        ._accessURL_(URI.create("http://example.com/endpoint/uriormodelclasscorrecttranslation/1"))
                        .build()
                )
                ._inboundModelVersion_("4.4.4")
                ._outboundModelVersion_("4.4.4")
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                .build();
        String baseConnectorAsString = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(baseConnector);
        logger.info(baseConnectorAsString);
        BaseConnector recreated = new ObjectMapper().readValue(baseConnectorAsString, BaseConnectorImpl.class);
        Assert.assertEquals(baseConnector, recreated);
        Assert.assertEquals(SecurityProfile.BASE_SECURITY_PROFILE, recreated.getSecurityProfile());
        logger.info(new ObjectMapper().writer().writeValueAsString(recreated));
    }

    @Test
    public void EnumWithJacksonNoTypeStatement() throws JsonProcessingException {
        String baseConnectorAsString = "{\n" +
                "  \"@type\" : \"ids:BaseConnector\",\n" +
                "  \"publicKey\" : null,\n" +
                "  \"version\" : null,\n" +
                "  \"description\" : [ ],\n" +
                "  \"securityProfile\" : {\n" +
//                "    \"@type\" : \"ids:SecurityProfile\",\n" +
                "    \"properties\" : null,\n" +
                "    \"@id\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"\n" +
                "  },\n" +
                "  \"curatorAsUri\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/1\",\n" +
                "  \"curatorAsObject\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"version\" : \"1\",\n" +
                "    \"description\" : [ ],\n" +
                "    \"jurisdiction\" : null,\n" +
                "    \"primarySite\" : null,\n" +
                "    \"vatID\" : null,\n" +
                "    \"legalForm\" : \"Very legal\",\n" +
                "    \"legalName\" : [ ],\n" +
                "    \"memberPerson\" : [ ],\n" +
                "    \"title\" : [ ],\n" +
                "    \"corporateEmailAddress\" : [ ],\n" +
                "    \"corporateHomepage\" : null,\n" +
                "    \"participantCertification\" : null,\n" +
                "    \"memberParticipant\" : [ ],\n" +
                "    \"participantRefinement\" : null,\n" +
                "    \"businessIdentifier\" : [ ],\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/ed5c1dc6-442c-4715-a0d6-290020385d97\"\n" +
                "  },\n" +
                "  \"maintainerAsUri\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/2\",\n" +
                "  \"hasDefaultEndpoint\" : {\n" +
                "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
                "    \"path\" : null,\n" +
                "    \"inboundPath\" : null,\n" +
                "    \"outboundPath\" : null,\n" +
                "    \"accessURL\" : \"http://example.com/endpoint/uriormodelclasscorrecttranslation/1\",\n" +
                "    \"apiSpecifiation\" : [ ],\n" +
                "    \"endpointDocumentation\" : [ ],\n" +
                "    \"endpointInformation\" : [ ],\n" +
                "    \"endpointArtifact\" : null,\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/c21957e7-c10d-40f3-8052-71e3222141f8\"\n" +
                "  },\n" +
                "  \"extendedGuarantee\" : [ ],\n" +
                "  \"outboundModelVersion\" : \"4.4.4\",\n" +
                "  \"componentCertification\" : null,\n" +
                "  \"inboundModelVersion\" : [ \"4.4.4\" ],\n" +
                "  \"physicalLocation\" : null,\n" +
                "  \"maintainerAsObject\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"version\" : \"2\",\n" +
                "    \"description\" : [ ],\n" +
                "    \"jurisdiction\" : null,\n" +
                "    \"primarySite\" : null,\n" +
                "    \"vatID\" : null,\n" +
                "    \"legalForm\" : \"illegal\",\n" +
                "    \"legalName\" : [ ],\n" +
                "    \"memberPerson\" : [ ],\n" +
                "    \"title\" : [ ],\n" +
                "    \"corporateEmailAddress\" : [ ],\n" +
                "    \"corporateHomepage\" : null,\n" +
                "    \"participantCertification\" : null,\n" +
                "    \"memberParticipant\" : [ ],\n" +
                "    \"participantRefinement\" : null,\n" +
                "    \"businessIdentifier\" : [ ],\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/89829290-0ab8-475d-80be-0904762d82ba\"\n" +
                "  },\n" +
                "  \"hasAgent\" : [ \"http://example.com/participant/uriormodelclasscorrecttranslation/2\" ],\n" +
                "  \"title\" : [ ],\n" +
                "  \"resourceCatalog\" : [ ],\n" +
                "  \"hasEndpoint\" : [ ],\n" +
                "  \"authInfo\" : null,\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/baseConnector/e546fdf5-2810-4b2e-b407-76db5680fbbb\"\n" +
                "}\n";
        try {
            BaseConnector recreated = new ObjectMapper().readValue(baseConnectorAsString, BaseConnectorImpl.class);
            logger.info(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(recreated));
        } catch (InvalidTypeIdException invalidTypeIdException) {
            // expected error, because the type is missing
            return;
        }
        Assert.fail();
    }

    @Test
    public void EnumWithJacksonWithTypeStatement() throws JsonProcessingException {
        String baseConnectorAsString = "{\n" +
                "  \"@type\" : \"ids:BaseConnector\",\n" +
                "  \"publicKey\" : null,\n" +
                "  \"version\" : null,\n" +
                "  \"description\" : [ ],\n" +
                "  \"securityProfile\" : {\n" +
                "    \"@type\" : \"ids:SecurityProfile\",\n" +
                "    \"properties\" : null,\n" +
                "    \"@id\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"\n" +
                "  },\n" +
                "  \"curatorAsUri\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/1\",\n" +
                "  \"curatorAsObject\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"version\" : \"1\",\n" +
                "    \"description\" : [ ],\n" +
                "    \"jurisdiction\" : null,\n" +
                "    \"primarySite\" : null,\n" +
                "    \"vatID\" : null,\n" +
                "    \"legalForm\" : \"Very legal\",\n" +
                "    \"legalName\" : [ ],\n" +
                "    \"memberPerson\" : [ ],\n" +
                "    \"title\" : [ ],\n" +
                "    \"corporateEmailAddress\" : [ ],\n" +
                "    \"corporateHomepage\" : null,\n" +
                "    \"participantCertification\" : null,\n" +
                "    \"memberParticipant\" : [ ],\n" +
                "    \"participantRefinement\" : null,\n" +
                "    \"businessIdentifier\" : [ ],\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/ed5c1dc6-442c-4715-a0d6-290020385d97\"\n" +
                "  },\n" +
                "  \"maintainerAsUri\" : \"http://example.com/participant/uriormodelclasscorrecttranslation/2\",\n" +
                "  \"hasDefaultEndpoint\" : {\n" +
                "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
                "    \"path\" : null,\n" +
                "    \"inboundPath\" : null,\n" +
                "    \"outboundPath\" : null,\n" +
                "    \"accessURL\" : \"http://example.com/endpoint/uriormodelclasscorrecttranslation/1\",\n" +
                "    \"apiSpecifiation\" : [ ],\n" +
                "    \"endpointDocumentation\" : [ ],\n" +
                "    \"endpointInformation\" : [ ],\n" +
                "    \"endpointArtifact\" : null,\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/c21957e7-c10d-40f3-8052-71e3222141f8\"\n" +
                "  },\n" +
                "  \"extendedGuarantee\" : [ ],\n" +
                "  \"outboundModelVersion\" : \"4.4.4\",\n" +
                "  \"componentCertification\" : null,\n" +
                "  \"inboundModelVersion\" : [ \"4.4.4\" ],\n" +
                "  \"physicalLocation\" : null,\n" +
                "  \"maintainerAsObject\" : {\n" +
                "    \"@type\" : \"ids:Participant\",\n" +
                "    \"version\" : \"2\",\n" +
                "    \"description\" : [ ],\n" +
                "    \"jurisdiction\" : null,\n" +
                "    \"primarySite\" : null,\n" +
                "    \"vatID\" : null,\n" +
                "    \"legalForm\" : \"illegal\",\n" +
                "    \"legalName\" : [ ],\n" +
                "    \"memberPerson\" : [ ],\n" +
                "    \"title\" : [ ],\n" +
                "    \"corporateEmailAddress\" : [ ],\n" +
                "    \"corporateHomepage\" : null,\n" +
                "    \"participantCertification\" : null,\n" +
                "    \"memberParticipant\" : [ ],\n" +
                "    \"participantRefinement\" : null,\n" +
                "    \"businessIdentifier\" : [ ],\n" +
                "    \"@id\" : \"https://w3id.org/idsa/autogen/participant/89829290-0ab8-475d-80be-0904762d82ba\"\n" +
                "  },\n" +
                "  \"hasAgent\" : [ \"http://example.com/participant/uriormodelclasscorrecttranslation/2\" ],\n" +
                "  \"title\" : [ ],\n" +
                "  \"resourceCatalog\" : [ ],\n" +
                "  \"hasEndpoint\" : [ ],\n" +
                "  \"authInfo\" : null,\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/baseConnector/e546fdf5-2810-4b2e-b407-76db5680fbbb\"\n" +
                "}\n";
        BaseConnector recreated = new ObjectMapper().readValue(baseConnectorAsString, BaseConnectorImpl.class);
        String recreatedAsString = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(recreated);
        logger.info(recreatedAsString);
    }

    @Test
    public void InterFaceInstanceInListTest() throws IOException {
        UsageControlObject usageControlObject = new UsageControlObjectBuilder()
                ._action_(Action.ENCRYPT)._action_(Action.USE)
                ._created_(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2021-03-17T10:45:19.484+01:00"))
                ._data_(URI.create("http://example.com/123"))
                .build();
        Serializer serializer = new Serializer();
        String usageControlObjectAsString = serializer.serialize(usageControlObject);
        logger.info(usageControlObjectAsString);
        UsageControlObject recreated = serializer.deserialize(usageControlObjectAsString, UsageControlObject.class);
        Assert.assertEquals(recreated, usageControlObject);
    }

    // TODO: Use sets in equals method or in general
    @Ignore
    @Test
    public void InterFaceInstanceInListButOrderMattersTest() throws IOException {
        UsageControlObject usageControlObject = new UsageControlObjectBuilder()
                ._action_(Action.USE)._action_(Action.ENCRYPT)
                ._created_(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2021-03-17T10:45:19.484+01:00"))
                ._data_(URI.create("http://example.com/123"))
                .build();
        Serializer serializer = new Serializer();
        String usageControlObjectAsString = serializer.serialize(usageControlObject);
        logger.info(usageControlObjectAsString);
        UsageControlObject recreated = serializer.deserialize(usageControlObjectAsString, UsageControlObject.class);
        Assert.assertEquals(recreated, usageControlObject);
    }

    @Test
    public void InterfaceInstanceFromJsonWithoutTypeInListTest() throws IOException {
        String jsonString = "{\n" +
                "  \"@context\" : {\n" +
                "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
                "  },\n" +
                "  \"@type\" : \"ids:UsageControlObject\",\n" +
                "  \"@id\" : \"https://w3id.org/idsa/autogen/usageControlObject/a1baba68-84ec-486a-a3ed-950038a09a42\",\n" +
                "  \"ids:data\" : {\n" +
                "    \"@id\" : \"http://example.com/123\"\n" +
                "  },\n" +
                "  \"ids:created\" : {\n" +
                "    \"@value\" : \"2021-03-17T10:45:19.484+01:00\",\n" +
                "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
                "  },\n" +
                "  \"ids:action\" : [ {\n" +
                "    \"@id\" : \"https://w3id.org/idsa/code/ENCRYPT\"\n" +
                "  }, {\n" +
                "    \"@id\" : \"https://w3id.org/idsa/code/USE\"\n" +
                "  } ]\n" +
                "}";
        UsageControlObject usageControlObject = new Serializer().deserialize(jsonString, UsageControlObject.class);
        Assert.assertEquals(2, usageControlObject.getAction().size());
    }
}
