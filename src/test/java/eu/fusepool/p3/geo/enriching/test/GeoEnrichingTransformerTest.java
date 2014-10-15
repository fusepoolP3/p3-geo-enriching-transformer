package eu.fusepool.p3.geo.enriching.test;

import java.net.ServerSocket;
import java.util.Iterator;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jayway.restassured.RestAssured;

import eu.fusepool.p3.transformer.server.TransformerServer;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import eu.fusepool.p3.geo.enriching.GeoEnrichingTransformer;
import eu.fusepool.p3.transformer.client.Transformer;
import eu.fusepool.p3.transformer.client.TransformerClientImpl;
import eu.fusepool.p3.transformer.commons.Entity;
import eu.fusepool.p3.transformer.commons.util.WritingEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.ontologies.FOAF;
import org.apache.clerezza.rdf.utils.GraphNode;

import org.junit.Rule;

public class GeoEnrichingTransformerTest {
	private static final UriRef LONG = new UriRef("ttp://www.w3.org/2003/01/geo/wgs84_pos#long");
    private static final UriRef LAT = new UriRef("ttp://www.w3.org/2003/01/geo/wgs84_pos#lat");
    private static MimeType turtle;
    static {
        try {
            turtle = new MimeType("text/turtle");
        } catch (MimeTypeParseException ex) {
            Logger.getLogger(GeoEnrichingTransformerTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
	private static int mockPort = 0;
    private byte[] mockServerDataSet;
    private String transformerBaseUri;
	
	//private final int WIREMOCK_PORT = 8089;
	@BeforeClass
	public static void setMockPort() {
		mockPort = findFreePort();
	}
    
	
	@Before
    public void setUp() throws Exception {
		
		mockServerDataSet = IOUtils.toByteArray(getClass().getResourceAsStream("farmacie-trentino.ttl"));	
		final int transformerServerPort = findFreePort();
        transformerBaseUri = "http://localhost:" + transformerServerPort + "/";
        RestAssured.baseURI = transformerBaseUri;
        TransformerServer server = new TransformerServer(transformerServerPort);
        server.start(new GeoEnrichingTransformer());
    
	}
	
	
	@Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);
    
    @Test
    public void testTurtleSupported()  throws MimeTypeParseException {
        Transformer t = new TransformerClientImpl(RestAssured.baseURI);
        Set<MimeType> types = t.getSupportedInputFormats();
        Assert.assertTrue("No supported Output format", types.size() > 0);
        boolean turtleFound = false;
        for (MimeType mimeType : types) {
            if (turtle.match(mimeType)) {
                turtleFound = true;
            }
        }
        Assert.assertTrue("None of the supported output formats is turtle", turtleFound);
    }
        

	@Test
    public void testRemoteConfig() throws Exception {
		stubFor(get(urlEqualTo("/data/farmacie-trentino.ttl"))
	    	    .willReturn(aResponse()
	    			.withStatus(HttpStatus.SC_OK)
	    			.withHeader("Content-Type", "text/turtle")
	    			.withBody(mockServerDataSet)));
        
		final MGraph graphToEnrich = new SimpleMGraph();
        final UriRef res1 = new UriRef("http://example.org/res1");
        final GraphNode node = new GraphNode(res1, graphToEnrich);
        node.addPropertyValue(LAT, "46.2220374200606");
        node.addPropertyValue(LONG, "10.7963137713743");
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serializer.getInstance().serialize(baos, graphToEnrich, "text/turtle");
        final byte[] ttlData = baos.toByteArray();
        
        Transformer t = new TransformerClientImpl(RestAssured.baseURI);
        Entity response = t.transform(new WritingEntity() {

            @Override
            public MimeType getType() {
                return turtle;
            }

            @Override
            public void writeData(OutputStream out) throws IOException {
                out.write(ttlData);
            }
        }, turtle);
    	
        Assert.assertEquals("Wrong media Type of response", turtle, response.getType());
        final Graph responseGraph = Parser.getInstance().parse(response.getData(), "text/turtle");
        //is there a better property for nearby?
        final Iterator<Triple> baseNearIter = responseGraph.filter(res1, FOAF.based_near, null);
    	Assert.assertTrue("No base_near property on res1 in response", baseNearIter.hasNext());
    	
    }
    
	
	public static int findFreePort() {
        int port = 0;
        try (ServerSocket server = new ServerSocket(0);) {
            port = server.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("unable to find a free port");
        }
        return port;
    }

}
