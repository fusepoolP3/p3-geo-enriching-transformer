Fusepool P3 Geo-enriching Transformer
============================

A transformer enriching RDF data containing the position of the client with information about events and points of interest nearby this position.
Implements the requirement in [FP-205](https://fusepool.atlassian.net/browse/FP-205).

[![Build Status](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer.svg)](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer)

Compile the application running the command

    mvn install

Start the application using the command

    mvn exec:java

To test the transformer send an http post message with the data containing the position of the client, a date eventually, and the url of the data set in which to search for points of interest nearby it. The position of the client must be described like in the following  

    @prefix geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
    @prefix schema: <http://schema.org/> .

    <urn:uuid:fusepoolp3:myplace> geo:lat "45.92"^^xsd:double ;
                                  geo:long "10.89"^^xsd:double ;
                                  schema:event <urn:uuid:fusepoolp3:myevent> .
    <urn:uuid:fusepoolp3:myevent> schema:startDate "2015-09-01"^^xsd:date .

If you store the above example content in a file `test_geo_enricher.ttl` you can Use cURL to send the HTTP POST request as follows:

    curl -i -X POST -H "Content-Type: text/turtle" -d @test_geo_enricher.ttl http://localhost:7100/?graph=file:///home/user/eventi.ttl

If the data set can be put in a server use its http url in place of the file url. The result is a graph of points of interest close to the location specified by the client in the request data

    <urn:uuid:fusepoolp3:pharmacy:16670>
        a       <http://schema.org/Pharmacy> ;
        <http://www.w3.org/2000/01/rdf-schema#label>
                "Farmacia COMUNALE N.3 PIO X" ;
        <http://www.w3.org/2003/01/geo/wgs84_pos#lat>
                "46.0524938275703"^^<http://www.w3.org/2001/XMLSchema#float> ;
        <http://www.w3.org/2003/01/geo/wgs84_pos#long>
                "11.1198202997403"^^<http://www.w3.org/2001/XMLSchema#float> ;
        <http://xmlns.com/foaf/0.1/based_near>
                <urn:uuid:fusepoolp3:myplace> .

In case a date was given related to the position by a schema:startDate predicate, as in the above example, the points of interest are intended to be locations of events and will be filtered further so that only events nearby that happen after that date will be returned. 
