Geo-enriching Transformer [![Build Status](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer.svg)](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer)
=========================

A transformer enriching RDF data containing the position of the client with information about events and points of interest nearby this position.
Implements the requirement in [FP-205](https://fusepool.atlassian.net/browse/FP-205).

## Try it out

First, obtain the latest [release](https://github.com/fusepoolP3/p3-geo-enriching-transformer/releases/latest).

Next, start the transformer:

    java -jar geo-enriching-transformer-v*-jar-with-dependencies.jar

To obtain the supported input/output-formats of the transformer, query it with the curl-utility:

    curl -X GET -H "ContentType: text/turtle" http://localhost:7100

For advanced testing of the transformer, refer to the section "Usage" just below.

## Compiling and Running
Compile the transformer using maven:

    mvn install

To start the compiled transformer, also use maven:

    mvn exec:java

## Usage
To search for points of interest around a place send an http post message with the data containing its geographic coordinates position and the URL of the data set in which to search for points of interest nearby it. The position of the client must be described like in the following  

    @prefix geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
    @prefix schema: <http://schema.org/> .

    <urn:uuid:fusepoolp3:myplace> geo:lat "45.92"^^xsd:double ;
                                  geo:long "10.89"^^xsd:double ;
                                  schema:geo <urn:uuid:fusepoolp3:mycircle> .
    <urn:uuid:fusepoolp3:mycircle> schema:circle "45.92 10.89 100" .

The value of schema:circle property represents latitude, longitude and radius of a circular region.
If you store the above example content in a file `test_geo_enricher.ttl` you can Use cURL to send the HTTP POST request as follows:

    curl -i -X POST -H "Content-Type: text/turtle" -d @test_geo_enricher.ttl http://localhost:7100/?graph=file:///home/user/eventi.ttl

If the data set can be put in a server use its http url in place of the file url. The result is a graph of points of interest within the area specified by the client in the request data

    <urn:location:uuid:ba9b8c15-237b-48cb-bc72-9cb9512b61f1>
                                  a       <http://schema.org/Place> ;
                    <http://www.w3.org/2000/01/rdf-schema#label> "Palazzo dei Panni" ;
                    <http://schema.org/containedIn> <urn:uuid:fusepoolp3:myplace> ;
                    <http://www.w3.org/2003/01/geo/wgs84_pos#lat> "45.92037"^^<http://www.w3.org/2001/XMLSchema#float> ;
                    <http://www.w3.org/2003/01/geo/wgs84_pos#long> "10.88906"^^<http://www.w3.org/2001/XMLSchema#float> .


In case a date is given related to the position by a schema:startDate predicate the points of interest are intended to be locations of events and will be filtered further so that only events nearby that happen after that date, and before an end date if available, will be returned. To search for events that start from a certain date within a circular area around a place send an http post message with the data containing the starting date, the geographic coordinates of the position and the URL of the data set in which to search for events nearby it like in the following

    @prefix geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
    @prefix schema: <http://schema.org/> .

    <urn:uuid:fusepoolp3:myevent> schema:startDate "2015-02-01"^^xsd:date ;
                                  schema:endDate "2015-02-28"^^xsd:date ;
                                  schema:location <urn:uuid:fusepoolp3:myplace> .
    <urn:uuid:fusepoolp3:myplace> geo:lat "45.92"^^xsd:double ;
                                  geo:long "10.89"^^xsd:double ;
                                  schema:geo <urn:uuid:fusepoolp3:mycircle> .
    <urn:uuid:fusepoolp3:mycircle> schema:circle "45.92 10.89 500" .

The result is a graph with events within the given time frame in an aerea within a circular area around the given position

    <urn:event:uuid:e1cee573-042d-4cf4-bc10-6e547f127167>
        <http://www.w3.org/2000/01/rdf-schema#label> "Gnocchi in piazza" ;
        <http://schema.org/startDate>  "2015-02-13"^^<http://www.w3.org/2001/XMLSchema#double> .
        <http://schema.org/endDate>    "2015-02-13"^^<http://www.w3.org/2001/XMLSchema#double> ;
        <http://schema.org/location>   <urn:location:uuid:e1cee573-042d-4cf4-bc10-6e547f127167> .
    <urn:location:uuid:e1cee573-042d-4cf4-bc10-6e547f127167>
        <http://www.w3.org/2000/01/rdf-schema#label> "Arco, Piazza III Novembre, Varignano e Bolognano - Dro, Piazza Repubblica" ;
        <http://schema.org/containedIn> <urn:uuid:fusepoolp3:myplace> ;
        <http://schema.org/event>  <urn:event:uuid:e1cee573-042d-4cf4-bc10-6e547f127167> ;
        <http://www.w3.org/2003/01/geo/wgs84_pos#lat> "45.91897"^^<http://www.w3.org/2001/XMLSchema#float> ;
        <http://www.w3.org/2003/01/geo/wgs84_pos#long> "10.88580"^^<http://www.w3.org/2001/XMLSchema#float> .
        
