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

import java.io.File;
import java.io.IOException;
import java.net.URL;

import junit.framework.Assert;

import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.query.spatial.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.util.QueryExecUtils;
import com.hp.hpl.jena.tdb.TDBFactory;

import eu.fusepool.p3.geo.enriching.SpatialDataEnhancer;

import org.junit.Test;

/**
 * Build a spatial search dataset
 */
public class JenaSpatialTest {

    final String TEST_DATASET = "spatial-data-latlong.ttl";    
    SpatialDataEnhancer jenas = null;
    
    static {
        LogCtl.setLog4j();
    }
    static Logger log = LoggerFactory.getLogger("JenaSpatialTest");

    @Before
    public void setUp() throws Exception {
        jenas = new SpatialDataEnhancer();        
    }

    @Test
    public void testJenaSpatial() throws IOException {
        URL testFile = getClass().getResource(TEST_DATASET);
        jenas.loadData(jenas.getDataset(), testFile.getFile());
        queryData(jenas.getDataset());
    }
    
    public void queryData(Dataset spatialDataset) {
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
