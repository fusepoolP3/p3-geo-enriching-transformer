/*
 * Copyright 2014 Bern University of Applied Sciences.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fusepool.p3.geo.enriching;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.p3.transformer.HttpRequestEntity;
import eu.fusepool.p3.transformer.RdfGeneratingTransformer;

/**
 * A transformer geo-enriching Data against a URI specified at construction.
 */
class GeoEnrichingTransformer extends RdfGeneratingTransformer {


    private static final Logger log = LoggerFactory.getLogger(GeoEnrichingTransformer.class);
    
    final SpatialDataEnhancer spatialDataEnhancer;
    final String kbDataUrl;

    GeoEnrichingTransformer(SpatialDataEnhancer spatialDataEnhancer, String kbDataUrl) {
        this.spatialDataEnhancer = spatialDataEnhancer;
        this.kbDataUrl = kbDataUrl;
    }

    @Override
    public Set<MimeType> getSupportedInputFormats() {
        Parser parser = Parser.getInstance();
        try {
            Set<MimeType> mimeSet = new HashSet<MimeType>();
            for (String mediaFormat : parser.getSupportedFormats()) {           
              mimeSet.add(new MimeType(mediaFormat));
            }
            return Collections.unmodifiableSet(mimeSet);
        } catch (MimeTypeParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Takes the RDF data sent by the client and the graph name (url) of the knowledge base to search
     * for points of interest nearby the locations described in the client graph and sends it back enriched with
     * information about the points of interest that have been found.   
     */
    @Override
    protected TripleCollection generateRdf(HttpRequestEntity entity) throws IOException {
        String mediaType = entity.getType().toString();   
        Parser parser = Parser.getInstance();
        TripleCollection requestedGraph = parser.parse( entity.getData(), mediaType);
        //TODO use : return spatialDataEnhancer.enhance(kbDataUrl, requestedGraph);
        return requestedGraph;
    }
  
    @Override
    public boolean isLongRunning() {
        // downloading the dataset can be time consuming
        return true;
    }

}
