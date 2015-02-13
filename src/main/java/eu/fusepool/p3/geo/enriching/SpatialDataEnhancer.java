package eu.fusepool.p3.geo.enriching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.TypedLiteral;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.ontologies.FOAF;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.ontologies.XSD;
import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.query.spatial.EntityDefinition;
import org.apache.jena.query.spatial.SpatialDatasetFactory;
import org.apache.jena.query.spatial.SpatialIndex;
import org.apache.jena.query.spatial.SpatialIndexLucene;
import org.apache.jena.query.spatial.SpatialQuery;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.tdb.TDBFactory;
/**
 * Enhances an input graph with information taken from a remote source. 
 * @author luigi
 *
 */
public class SpatialDataEnhancer {
    
    private static final UriRef geo_long = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#long");
    private static final UriRef geo_lat = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#lat");
    private static final UriRef schema_event = new UriRef("http://schema.org/event");
    private static final UriRef schema_startDate = new UriRef("http://schema.org/startDate");
    private static final UriRef schema_endDate = new UriRef("http://schema.org/endDate");
    
    File LUCENE_INDEX_DIR = null;
    File TDB_DIR = null;
    Dataset spatialDataset = null;
    
    static {
        LogCtl.setLog4j();
    }
    static Logger log = LoggerFactory.getLogger("JenaSpatial");
    
    public SpatialDataEnhancer() throws IOException {
        LUCENE_INDEX_DIR = File.createTempFile("lucene-", "-index");
        TDB_DIR = File.createTempFile("jenatdb-", "-dataset");
        //spatialDataset = initInMemoryDatasetWithLuceneSpatialIndex(LUCENE_INDEX_DIR);
        spatialDataset = initTDBDatasetWithLuceneSpatialIndex(LUCENE_INDEX_DIR, TDB_DIR);
    }
    /**
     * Takes a RDF data set to search for point of interest close to objects provided in a graph.  
     * @throws ParseException 
     * @throws Exception 
     */
    public TripleCollection enhance(String dataSetUrl, TripleCollection dataToEnhance) {
        TripleCollection result = new SimpleMGraph();
        if( dataToEnhance != null ){
        	if( ! dataToEnhance.isEmpty() ) {
		        result.addAll(dataToEnhance);
		        //look for the knowledge base name in the triple store before fetching the data from the url.
		        if( ! isCachedGraph(spatialDataset, dataSetUrl) ){
		          loadKnowledgeBase(spatialDataset, dataSetUrl, dataSetUrl);
		        }
		        else {
		            log.info("Rdf data set " + dataSetUrl + " already in the triple store.");
		        }
		        WGS84Point point = getPoint(dataToEnhance);
		        if(point.getStartDate() != null || point.getEndDate() != null){ 
    		        TripleCollection poiGraph = queryEventsNearby(point, dataSetUrl, 1);
    		        if(poiGraph.size() > 0){
    		         result.addAll(poiGraph);
    		        }
		        }
		        else {
		            TripleCollection poiGraph = queryNearby(point, dataSetUrl, 1);
                    if(poiGraph.size() > 0){
                     result.addAll(poiGraph);
                    }
		        }
        	}
        	else {
        		throw new IllegalArgumentException("An empty graph cannot be enhanced");
        	}
        }
        else {
        	throw new NullPointerException("A null object has been passed instead of a graph.");
        }
        return result;
    }
    /**
     * Extracts one spatial point or event from the client data.
     * @param graph
     * @return
     * @throws ParseException 
     */
    public WGS84Point getPoint(TripleCollection graph) {
        WGS84Point point = new WGS84Point();   
        NonLiteral pointRef = graph.filter(null, geo_lat, null).next().getSubject();
        String latitude = ( (TypedLiteral) graph.filter(pointRef, geo_lat, null).next().getObject() ).getLexicalForm();
        String longitude = ( (TypedLiteral) graph.filter(pointRef, geo_long, null).next().getObject() ).getLexicalForm();
        point.setUri(pointRef.toString());
        point.setLat(Double.valueOf(latitude));
        point.setLong(Double.valueOf(longitude));
        // look for events linked to places
        if(graph.filter(pointRef, schema_event, null).hasNext()){
            NonLiteral event = (NonLiteral)graph.filter(pointRef, schema_event, null).next().getObject();        
            String startDate = ( (TypedLiteral) graph.filter(event, schema_startDate, null).next().getObject()).getLexicalForm();
            point.setStartDate(startDate);
        }
        return point;
    }
    /**
     * Searches for points of interest within a circle of a given radius. 
     * The data used is stored in a named graph.
     * @param point
     * @param uri
     * @param radius
     * @return
     */
    public TripleCollection queryNearby(WGS84Point point, String graphName, int radius){
        TripleCollection resultGraph = new SimpleMGraph();
        log.info("queryNearby()");
        long startTime = System.nanoTime();
        String pre = StrUtils.strjoinNL("PREFIX spatial: <http://jena.apache.org/spatial#>",
                "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>",
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>");
        
        String qs = StrUtils.strjoinNL("SELECT * ",
                "FROM NAMED <" + graphName + ">",
                "WHERE { ",
                "GRAPH <" + graphName + "> ",
                " { ?s spatial:nearby (" + point.getLat() + " " + point.getLong() + " " + radius + " 'km') ;",
                "      rdf:type ?type ; ",
                "      geo:lat ?lat ;" ,
                "      geo:long ?lon ; ",
                
                "      rdfs:label ?label .", " }",
                "}");

        log.info(pre + "\n" + qs);
        spatialDataset.begin(ReadWrite.READ);
        int poiCounter = 0;
        try {
            Query q = QueryFactory.create(pre + "\n" + qs);
            QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; ) {
                QuerySolution solution = results.nextSolution() ;
                String poiUri = solution.getResource("s").getURI();
                String poiName = checkUriName(poiUri);
                String poiType = checkUriName(solution.getResource("type").getURI());
                String poiLabel = solution.getLiteral("label").getString();
                String poiLatitude = solution.getLiteral("lat").getString();
                String poiLongitude = solution.getLiteral("lon").getString();
                log.info("poi name: " + poiName + " label = " + poiLabel);
                UriRef poiRef = new UriRef(poiName);
                String pointName = checkUriName(point.getUriName());
                resultGraph.add( new TripleImpl(new UriRef(pointName), FOAF.based_near, poiRef) );               
                resultGraph.add( new TripleImpl(poiRef, RDFS.label, new PlainLiteralImpl(poiLabel)) );
                resultGraph.add( new TripleImpl(poiRef, RDF.type, new UriRef(poiType)));
                resultGraph.add( new TripleImpl(poiRef, geo_lat, new TypedLiteralImpl(poiLatitude, XSD.float_)) );
                resultGraph.add( new TripleImpl(poiRef, geo_long, new TypedLiteralImpl(poiLongitude, XSD.float_)) );  
                poiCounter++;
                
            }
          
        } 
        finally {
            spatialDataset.end();
        }
        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("FINISH - %.2fms", time));
        log.info(String.format("Found " + poiCounter + " points of interest."));
        return resultGraph;

    }
    /**
     * Searches for events within a circle of a given radius, starting from a date or within a time frame. 
     * The data used is stored in a named graph.
     * @param point
     * @param uri
     * @param radius
     * @return
     */
    public TripleCollection queryEventsNearby(WGS84Point point, String graphName, int radius){
        TripleCollection resultGraph = new SimpleMGraph();
        log.info("queryNearby()");
        long startTime = System.nanoTime();
        String pre = StrUtils.strjoinNL("PREFIX spatial: <http://jena.apache.org/spatial#>",
                "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>",
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>",
                "PREFIX schema: <http://schema.org/>",
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>");
        
        String qs = StrUtils.strjoinNL("SELECT * ",
                "FROM NAMED <" + graphName + ">",
                "WHERE { ",
                "GRAPH <" + graphName + "> ",
                " { ?location spatial:nearby (" + point.getLat() + " " + point.getLong() + " " + radius + " 'km') .",
                "   ?location geo:lat ?lat ." ,
                "   ?location geo:long ?lon . ",
                "   ?location rdf:type ?type . ",
                "   ?location rdfs:label ?label .",
                "   ?event schema:location ?location .",
                "   ?event schema:startDate ?start .",
                "   FILTER(?start >= \"" + point.getStartDate() + "\"^^xsd:date ) ",
                " }",
                "}");

        log.info(pre + "\n" + qs);
        spatialDataset.begin(ReadWrite.READ);
        int poiCounter = 0;
        try {
            Query q = QueryFactory.create(pre + "\n" + qs);
            QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; ) {
                QuerySolution solution = results.nextSolution() ;
                String poiUri = solution.getResource("location").getURI();
                String poiName = checkUriName(poiUri);
                String poiType = checkUriName(solution.getResource("type").getURI());
                String poiLabel = solution.getLiteral("label").getString();
                String poiLatitude = solution.getLiteral("lat").getString();
                String poiLongitude = solution.getLiteral("lon").getString();
                log.info("poi name: " + poiName + " label = " + poiLabel);
                UriRef poiRef = new UriRef(poiName);                
                String pointName = checkUriName(point.getUriName());
                resultGraph.add( new TripleImpl(new UriRef(pointName), FOAF.based_near, poiRef) );               
                resultGraph.add( new TripleImpl(poiRef, RDFS.label, new PlainLiteralImpl(poiLabel)) );
                resultGraph.add( new TripleImpl(poiRef, RDF.type, new UriRef(poiType)));
                resultGraph.add( new TripleImpl(poiRef, geo_lat, new TypedLiteralImpl(poiLatitude, XSD.float_)) );
                resultGraph.add( new TripleImpl(poiRef, geo_long, new TypedLiteralImpl(poiLongitude, XSD.float_)) );
                String eventUri = solution.getResource("event").getURI();
                String startDate = solution.getLiteral("start").getString();
                UriRef eventRef = new UriRef(eventUri);
                resultGraph.add(new TripleImpl(eventRef,schema_startDate,new TypedLiteralImpl(startDate, XSD.double_)));
                poiCounter++;
                
            }
          
        } 
        finally {
            spatialDataset.end();
        }
        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("FINISH - %.2fms", time));
        log.info(String.format("Found " + poiCounter + " points of interest."));
        return resultGraph;

    }
    /**
     * Extracts the name from the URI (removes '<' and '>' )
     * @param uri
     * @return
     */
    private String checkUriName(String uri){
        if(uri.startsWith("<")){
            uri = uri.substring(1);
        }
        if(uri.endsWith(">")){
            uri = uri.substring(0, uri.length() -1);
        }
        return uri;
    }
    
    private Dataset initInMemoryDatasetWithLuceneSpatialIndex(File indexDir) throws IOException {
        SpatialQuery.init();
        deleteOldFiles(indexDir);
        indexDir.mkdirs();
        return createDatasetByCode(indexDir);
    }

    private Dataset initTDBDatasetWithLuceneSpatialIndex(File indexDir, File TDBDir) throws IOException {
        SpatialQuery.init();
        deleteOldFiles(indexDir);
        deleteOldFiles(TDBDir);
        indexDir.mkdirs();
        TDBDir.mkdir();
        return createDatasetByCode(indexDir, TDBDir);
    }

    private void deleteOldFiles(File indexDir) {
        if (indexDir.exists()) {
            emptyAndDeleteDirectory(indexDir);
        }
    }
    
    private Dataset createDatasetByCode(File indexDir) throws IOException {
        // Base data
        Dataset ds1 = DatasetFactory.createMem();
        return joinDataset(ds1, indexDir);
    }

    private Dataset createDatasetByCode(File indexDir, File TDBDir) throws IOException {
        // Base data
        Dataset ds1 = TDBFactory.createDataset(TDBDir.getAbsolutePath());
        return joinDataset(ds1, indexDir);
    }

    private Dataset joinDataset(Dataset baseDataset, File indexDir) throws IOException {
        EntityDefinition entDef = new EntityDefinition("entityField", "geoField");

        // you need JTS lib in the classpath to run the examples
        //entDef.setSpatialContextFactory(SpatialQuery.JTS_SPATIAL_CONTEXT_FACTORY_CLASS);
        // set custom goe predicates
        
        entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://schema.org/latitude"), ResourceFactory.createResource("http://schema.org/longitude"));
        /*
        entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://localhost/jena_example/#latitude_2"), ResourceFactory.createResource("http://localhost/jena_example/#longitude_2"));
        entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_1"));
        entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_2"));
        */
        // Lucene, index in File system.
        Directory dir = FSDirectory.open(indexDir);

        // Join together into a dataset
        Dataset ds = SpatialDatasetFactory.createLucene(baseDataset, dir, entDef);

        return ds;
    }
    
    private void emptyAndDeleteDirectory(File dir) {
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File content : contents) {
                if (content.isDirectory()) {
                    emptyAndDeleteDirectory(content);
                } else {
                    content.delete();
                }
            }
        }
        dir.delete();
    }
    
    private void destroy(Dataset spatialDataset, File luceneIndex, File tdbDataset) {

        SpatialIndex index = (SpatialIndex) spatialDataset.getContext().get(SpatialQuery.spatialIndex);
        if (index instanceof SpatialIndexLucene) {
            deleteOldFiles(luceneIndex);
            deleteOldFiles(tdbDataset);
        }

    }
    /**
     * Load a knowledge base
     * @param spatialDataset
     * @param uri
     * @param url
     * @throws Exception
     */
    public void loadKnowledgeBase(Dataset spatialDataset, String url, String graphName)  {
        
        log.info("Start loading data from: " + url);
        long startTime = System.nanoTime();
        spatialDataset.begin(ReadWrite.WRITE);
        try {
            Model m = spatialDataset.getNamedModel(graphName);
            RDFDataMgr.read(m, url);
            spatialDataset.commit();
        } finally {
            spatialDataset.end();
        }
        
        long numberOfTriples = 0;
        
        spatialDataset.begin(ReadWrite.READ);
        try {
            Model m = spatialDataset.getNamedModel(graphName);
            numberOfTriples = m.size();
            spatialDataset.commit();
        } finally {
            spatialDataset.end();
        }

        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("Finish loading " + numberOfTriples + " triples in graph " + graphName + " - %.2fms", time));
       
    }
    
    public Dataset getDataset() {
        return spatialDataset;
    }
    
    
    /**
     * Loads the data to be used as the application knowledge base. 
     * @throws Exception
     */
    public InputStream importKnowledgebase(String sourceDataUrl) throws Exception{
        
        URL sourceUrl = new URL(sourceDataUrl);
        URLConnection connection = sourceUrl.openConnection();
        return connection.getInputStream();
    }
    
    public boolean isCachedGraph(Dataset dataset, String graphName){
        boolean isCached = false;
        dataset.begin(ReadWrite.READ);
        try {
            Iterator<String> inames = getDataset().listNames();
            while(inames.hasNext()){
                if( graphName.equals( inames.next() )) {
                     isCached = true;  
                }
            }
        }
        finally {
            dataset.end();
        }
        return isCached;
    }

}
