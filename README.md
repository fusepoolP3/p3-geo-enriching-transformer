Fusepool P3 Geo-enriching Transformer
============================

A transformer enriching RDF data containing locations with information about points of interest nearby these locations.




[![Build Status](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer.svg)](https://travis-ci.org/fusepoolP3/p3-geo-enriching-transformer)

Compile the application running the command

    mvn install

Start the application using the command 

    mvn exec:java

To test the transformer send an http post message with the data containing the position of the client and the url of the data set in which to search for points of interest nearby it. The position of the client must be described like in the following  

@prefix geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<urn:uuid:fusepoolp3:myplace> geo:lat "46.41"^^xsd:double ;
	geo:long "11.05"^^xsd:double .

    curl -i -X POST -H "Content-Type: text/turtle" -T test_geo_enricher.ttl http://localhost:7100/?data=https://raw.githubusercontent.com/fusepoolP3/p3-geo-enriching-transformer/master/src/test/resources/eu/fusepool/p3/geo/enriching/test/farmacie-trentino-grounded.ttl

The command start an asynchronous task and the server sends the following information to the client to tell where the result could be fetched

HTTP/1.1 100 Continue

HTTP/1.1 202 Accepted
Date: Tue, 21 Oct 2014 09:01:08 GMT
Location: /job/1bd4b0ad-5054-41ae-a429-949883a95f82
Transfer-Encoding: chunked
Server: Jetty(9.2.0.RC0)

In order to get the result the following http get request must be sent to the server

    curl http://localhost:7100/job/1bd4b0ad-5054-41ae-a429-949883a95f82
