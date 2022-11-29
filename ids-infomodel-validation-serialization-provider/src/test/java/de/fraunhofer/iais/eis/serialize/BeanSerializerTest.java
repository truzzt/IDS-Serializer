package de.fraunhofer.iais.eis.serialize;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.Util;
import de.fraunhofer.iais.eis.util.VocabUtil;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class BeanSerializerTest {

    @Test
    public void serializeConnectorSelfDescription() throws ConstraintViolationException, IOException, URISyntaxException {
        String jsonLd = createConnectorSelfDescription().toRdf();
        Model model = toModel(jsonLd);
        Assert.assertFalse(model.isEmpty());
    }

    private Connector createConnectorSelfDescription() throws MalformedURLException, URISyntaxException {
        URI maintainer = new URL("http://companyA.org/maintainer").toURI();
        URI curator = new URL("http://companyA.org/curator").toURI();
        ResourceCatalog emptyCatalog = new ResourceCatalogBuilder().build();
        return new BaseConnectorBuilder()
                ._maintainerAsUri_(maintainer)
                ._curatorAsUri_(curator)
                ._resourceCatalog_(Util.asList(emptyCatalog))
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                ._inboundModelVersion_(Util.asList("3.0.0"))
                ._outboundModelVersion_("3.0.0")
                ._hasDefaultEndpoint_(new ConnectorEndpointBuilder()._accessURL_(URI.create("http://example.org/connectorEndpoint")).build())
                .build();
    }

    private Model toModel(String jsonLd) throws IOException {
        InputStream in = IOUtils.toInputStream(jsonLd, "UTF-8");
        return Rio.parse(in, "/", RDFFormat.JSONLD);
    }

    @Test
    public void deserializeConnectorSelfDecription() throws MalformedURLException, URISyntaxException {
        String jsonLd = createConnectorSelfDescription().toRdf();
        Connector connector = VocabUtil.getInstance().fromRdf(jsonLd, Connector.class);
        Assert.assertNotNull(connector);
    }

}
