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
import eu.fusepool.p3.geo.enriching.GeoEnrichingTransformerFactory;
import eu.fusepool.p3.transformer.client.Transformer;
import eu.fusepool.p3.transformer.client.TransformerClientImpl;
import eu.fusepool.p3.transformer.commons.Entity;
import eu.fusepool.p3.transformer.commons.util.WritingEntity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.ontologies.FOAF;
import org.apache.clerezza.rdf.ontologies.XSD;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.junit.Rule;

public class GeoEnrichingTransformerTest {
	private static final UriRef LONG = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#long");
    private static final UriRef LAT = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#lat");
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
		
		mockServerDataSet = IOUtils.toByteArray(getClass().getResourceAsStream("farmacie-trentino-grounded.ttl"));	
		final int transformerServerPort = findFreePort();
        transformerBaseUri = "http://localhost:" + transformerServerPort + "/";
        RestAssured.baseURI = transformerBaseUri;
        TransformerServer server = new TransformerServer(transformerServerPort);
        server.start(new GeoEnrichingTransformerFactory());
    
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
    public void testTransformation() throws Exception {
	 // Set up a service in the mock server to respond to a get request that must be sent by the transformer 
        // to fetch the data 
        stubFor(get(urlEqualTo("/data/farmacie-trentino-grounded.ttl"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", "text/turtle")
                    .withBody(mockServerDataSet)));
        
        final MGraph graphToEnrich = new SimpleMGraph();
        final UriRef res1 = new UriRef("http://example.org/res1");
        final GraphNode node = new GraphNode(res1, graphToEnrich);
        node.addProperty(LAT, new TypedLiteralImpl("46.2220374200606", XSD.float_));
        node.addProperty(LONG, new TypedLiteralImpl("10.7963137713743", XSD.float_));
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serializer.getInstance().serialize(baos, graphToEnrich, "text/turtle");
        final byte[] ttlData = baos.toByteArray();
        String dataUrl = "http://localhost:" + mockPort + "/data/farmacie-trentino-grounded.ttl";
        // a client send a request to the transformer with the url of the data to be fetched
        Transformer t = new TransformerClientImpl(RestAssured.baseURI+"?data="+URLEncoder.encode(dataUrl, "UTF-8"));
        // the transformer fetches the data from the mock server, applies its transformation and sends the RDF result to the client
        {
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

            Assert.assertEquals("Wrong media Type of response", turtle.toString(), response.getType().toString());

            InputStream in = response.getData();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while((line = reader.readLine()) != null){
                System.out.println(line);
            }

            final Graph responseGraph = Parser.getInstance().parse(response.getData(), "text/turtle");
            //is there a better property for nearby?
            final Iterator<Triple> baseNearIter = responseGraph.filter(res1, FOAF.based_near, null);
            Assert.assertTrue("No base_near property on res1 in response", baseNearIter.hasNext());
            verify(1,getRequestedFor(urlEqualTo("/data/farmacie-trentino-grounded.ttl")));
        }
        //second call
        {
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

            Assert.assertEquals("Wrong media Type of response", turtle.toString(), response.getType().toString());

            InputStream in = response.getData();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while((line = reader.readLine()) != null){
                System.out.println(line);
            }

            final Graph responseGraph = Parser.getInstance().parse(response.getData(), "text/turtle");
            //is there a better property for nearby?
            final Iterator<Triple> baseNearIter = responseGraph.filter(res1, FOAF.based_near, null);
            Assert.assertTrue("No base_near property on res1 in response", baseNearIter.hasNext());
            //verify that the data has not been loaded from the server
            verify(1,getRequestedFor(urlEqualTo("/data/farmacie-trentino-grounded.ttl")));
        }
	    
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
