/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fusepool.p3.geo.enriching.test;

import java.io.File ;
import java.io.IOException ;

import org.apache.jena.atlas.lib.StrUtils ;
import org.apache.jena.atlas.logging.LogCtl ;
import org.apache.jena.query.spatial.* ;
import org.apache.jena.riot.RDFDataMgr ;
import org.apache.lucene.store.Directory ;
import org.apache.lucene.store.FSDirectory ;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.query.* ;
import com.hp.hpl.jena.rdf.model.Model ;
import com.hp.hpl.jena.rdf.model.ResourceFactory ;
import com.hp.hpl.jena.sparql.util.QueryExecUtils ;
import com.hp.hpl.jena.tdb.TDBFactory ;

/** Build a spatial search dataset */
public class JenaSpatialTest {
	static {
		LogCtl.setLog4j();
	}
	static Logger log = LoggerFactory.getLogger("JenaSpatialExample");
	
	@Before
    public void setUp() throws Exception {
	    
	}
	
	@After
    public void tearDown() throws Exception {
        
    }

	public static void main(String... argv) throws IOException {
	    final String LUCENE_INDEX_PATH = "src/test/resources/lucene";
	    //private static final File LUCENE_INDEX_DIR = new File(LUCENE_INDEX_PATH);
	    final File LUCENE_INDEX_DIR = File.createTempFile("lucene-", "-index");
	    final String TDB_PATH = "src/test/resources/dataset";
	    //final File TDB_DIR = new File(TDB_PATH);
	    final File TDB_DIR = File.createTempFile("jenatdb-", "-dataset");
	    final String JENA_SPATIAL_ASSEMBLER = "src/test/resources/eu/fusepool/p3/geo/enriching/test/jena-spatial-assembler.ttl";
	    
		Dataset spatialDataset = initInMemoryDatasetWithLuceneSpatitalIndex(LUCENE_INDEX_DIR);
		//Dataset spatialDataset = initTDBDatasetWithLuceneSpatitalIndex(LUCENE_INDEX_DIR, TDB_DIR);
		//Dataset spatialDataset = createLuceneAssembler(JENA_SPATIAL_ASSEMBLER) ;
		
		loadData(spatialDataset, "src/test/resources/eu/fusepool/p3/geo/enriching/test/spatial-data-latlong.ttl");
		queryData(spatialDataset);
		
		destroy(spatialDataset, LUCENE_INDEX_DIR, TDB_DIR);
	}
	
	private static void destroy(Dataset spatialDataset, File luceneIndex, File tdbDataset){

		SpatialIndex index = (SpatialIndex)spatialDataset.getContext().get(SpatialQuery.spatialIndex);
		if (index instanceof SpatialIndexLucene){
			deleteOldFiles(luceneIndex);
			deleteOldFiles(tdbDataset);
		}
		
		
	}
    private static void emptyAndDeleteDirectory(File dir) {
        File[] contents = dir.listFiles() ;
        if (contents != null) {
            for (File content : contents) {
                if (content.isDirectory()) {
                    emptyAndDeleteDirectory(content) ;
                } else {
                    content.delete() ;
                }
            }
        }
        dir.delete() ;
    }
    
    private static Dataset initInMemoryDatasetWithLuceneSpatitalIndex(File indexDir) throws IOException{
		SpatialQuery.init();
		deleteOldFiles(indexDir);
		indexDir.mkdirs();
		return createDatasetByCode(indexDir);
    }
    
    private static Dataset initTDBDatasetWithLuceneSpatitalIndex(File indexDir, File TDBDir) throws IOException{
		SpatialQuery.init();
		deleteOldFiles(indexDir);
		deleteOldFiles(TDBDir);
		indexDir.mkdirs();
		TDBDir.mkdir();
		return createDatasetByCode(indexDir, TDBDir);
    }
    
	private static void deleteOldFiles(File indexDir) {
		if (indexDir.exists())
			emptyAndDeleteDirectory(indexDir);
	}
	
	private static Dataset createDatasetByCode(File indexDir) throws IOException {
		// Base data
		Dataset ds1 = DatasetFactory.createMem();
		return joinDataset(ds1, indexDir);
	}
	
	private static Dataset createDatasetByCode(File indexDir, File TDBDir) throws IOException {
		// Base data
		Dataset ds1 = TDBFactory.createDataset(TDBDir.getAbsolutePath());
		return joinDataset(ds1, indexDir);
	}
	
	private static Dataset joinDataset(Dataset baseDataset, File indexDir) throws IOException{
		EntityDefinition entDef = new EntityDefinition("entityField", "geoField");
		
		// you need JTS lib in the classpath to run the examples
		//entDef.setSpatialContextFactory(SpatialQuery.JTS_SPATIAL_CONTEXT_FACTORY_CLASS);
		
		// set custom goe predicates
		entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://localhost/jena_example/#latitude_1"), ResourceFactory.createResource("http://localhost/jena_example/#longitude_1"));
		entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://localhost/jena_example/#latitude_2"), ResourceFactory.createResource("http://localhost/jena_example/#longitude_2"));
		entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_1"));
		entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_2"));
		

		// Lucene, index in File system.
		Directory dir = FSDirectory.open(indexDir);

		// Join together into a dataset
		Dataset ds = SpatialDatasetFactory.createLucene(baseDataset, dir, entDef);

		return ds;
	}

	public static Dataset createLuceneAssembler(String assemblerFile) {
		log.info("Construct lucene spatial dataset using an assembler description");
		Dataset ds = DatasetFactory.assemble(assemblerFile, "http://localhost/jena_example/#spatial_dataset");
		return ds;
	}
	

	public static void loadData(Dataset spatialDataset, String file) {
		log.info("Start loading");
		long startTime = System.nanoTime();
		spatialDataset.begin(ReadWrite.WRITE);
		try {
			Model m = spatialDataset.getDefaultModel();
			RDFDataMgr.read(m, file);
			spatialDataset.commit();
		} finally {
			spatialDataset.end();
		}

		long finishTime = System.nanoTime();
		double time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("Finish loading - %.2fms", time));
	}

	public static void queryData(Dataset spatialDataset) {
		log.info("START");
		long startTime = System.nanoTime();
		String pre = StrUtils.strjoinNL("PREFIX : <http://example/>",
				"PREFIX spatial: <http://jena.apache.org/spatial#>",
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>");

		System.out.println("nearby");
		String qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:nearby (51.3000 -2.71000 100.0 'miles') ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		long finishTime = System.nanoTime();
		double time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));

		System.out.println("withinCircle");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:withinCircle (51.3000 -2.71000 100.0 'miles' 3) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		System.out.println("withinBox");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:withinBox (51.1000 -4.0000 51.4000 0.0000 -1) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));

		System.out.println("interesectBox");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:intersectBox (51.1000 -4.0000 51.4000 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		
		System.out.println("north");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:north (51.3000 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		System.out.println("south");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:south (51.3000 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		System.out.println("east");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:east (51.3000 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		System.out.println("west");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:west (51.3000 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
		
		System.out.println("west2");
		startTime = System.nanoTime();
		qs = StrUtils.strjoinNL("SELECT * ",
				" { ?s spatial:withinBox (51.1 -180.0000 51.9 0.0000) ;",
				"      rdfs:label ?label", " }");

		spatialDataset.begin(ReadWrite.READ);
		try {
			Query q = QueryFactory.create(pre + "\n" + qs);
			QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
			QueryExecUtils.executeQuery(q, qexec);
		} finally {
			spatialDataset.end();
		}
		finishTime = System.nanoTime();
		time = (finishTime - startTime) / 1.0e6;
		log.info(String.format("FINISH - %.2fms", time));
	}

}
