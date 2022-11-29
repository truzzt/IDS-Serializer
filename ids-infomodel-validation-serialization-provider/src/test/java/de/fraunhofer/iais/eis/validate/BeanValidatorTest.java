package de.fraunhofer.iais.eis.validate;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.Util;
import org.junit.Assert;
import org.junit.Test;


import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.GregorianCalendar;

public class BeanValidatorTest {

    @Test(expected = ConstraintViolationException.class)
    public void incompleteConnectorSelfDescription() throws ConstraintViolationException {
        new BaseConnectorBuilder().build();
    }

    @Test
    public void completeConnectorSelfDescription() throws ConstraintViolationException, MalformedURLException, URISyntaxException {
        URI maintainer = new URL("http://companyA.org/maintainer").toURI();
        URI curator = new URL("http://companyA.org/curator").toURI();
        ResourceCatalog emptyCatalog = new ResourceCatalogBuilder().build();

        // no ConstraintViolationException occurs here
        new BaseConnectorBuilder()
                ._maintainerAsUri_(maintainer)
                ._curatorAsUri_(curator)
                ._resourceCatalog_(Util.asList(emptyCatalog))
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                ._inboundModelVersion_(Util.asList("3.0.0"))
                ._outboundModelVersion_("3.0.0")
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()._accessURL_(URI.create("http://example.org/connectorEndpoint")).build())
                .build();
    }

    @Test
    public void incompleteMessage() throws ConstraintViolationException, MalformedURLException, DatatypeConfigurationException, URISyntaxException {
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(new Date());
        XMLGregorianCalendar now = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);

        try {
            new ContractAgreementMessageBuilder()
                    ._issuerConnector_(new URL("https://companyA.com/connector").toURI())
                    ._issued_(now)
                    .build();
        }
        catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getMessages().stream().anyMatch(msg -> msg.contains("modelVersion")));
        }
    }

}
