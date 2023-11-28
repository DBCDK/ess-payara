# external-search-system (Payara)

Denne service er en migreret version af "external-search-system" (<https://github.com/DBCDK/external-search-system>) til Payara.
Det er en service som faciliterer universal search, hvor resultater kan returneres i formater understøttet i Open Format.

## Kørsel
Servicen er et Payara projekt med config defineret i `service/src/main/resources/config.yaml`. 
Følgende environment-variabler er obligatoriske:
 - `BASES`: Hvilke baser som servicen tillader at efterspørge meta proxyen med til universal search.
 - `META_PROXY_URL`: Endpoint for meta proxy til universal search.
 - `OPEN_FORMAT_URL`: Url til en open format service.
 - `ESS_DB_URL`: ESS database URL til forbrugslogning
 
## Query parametre
 - `base`: Parameter der beskriver hvilken base der søges ned i. Exsempler inkluderer: `libris` og `bibsys`.
 - `query`: Efterspørgsel, kan formuleres i cql.
 - `format`: Ønsket output format. Gives direkte videre til Open Format.
 - `rows`: Antallet af rækker der ønskes returneret. Benyttes til paging.
 - `start`: Offset for de ønskede resultater, benyttes sammen med `rows` til paging. Defaulter til 0.
 
Eksempel URL:
`http://host:port/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=`
 
## Build docker image
Bygges med  `mvn clean package`.
 
 ## Run docker image
 
Her er et eksempel der viser hvordan docker imaget kan køres.
 
 `docker run -e ESS_DB_URL=[DB_USER]:[DB_PASS]@db.ess-v13.stg.dbc.dk:5432/ess_db -e "META_PROXY_URL=http://pz2-p01.dbc.dk:9001/" -e "OPEN_FORMAT_URL=http://open-format-broker.cisterne.svc.cloud.dbc.dk/api/v1/format" -e "BASES=bibsys,OLUCWorldCat,ArticleFirst" -ti --net=host -e JAVA_OPTS="-Dhazelcast.rest.enabled=true" docker-de.artifacts.dbccloud.dk/ess-payara-service-1.0:devel`
 
Endnu et eksempel, hvis du har brug for mere output til debug (i dette tilfælde fra klassen `Formatting`):

`docker run -e ESS_DB_URL=[DB_USER]:[DB_PASS]@db.ess-v13.stg.dbc.dk:5432/ess_db -e "META_PROXY_URL=http://pz2-p01.dbc.dk:9001/" -e "OPEN_FORMAT_URL=http://open-format-broker.cisterne.svc.cloud.dbc.dk/api/v1/format" -e "BASES=bibsys,OLUCWorldCat,ArticleFirst" -e LOG__dk_dbc=DEBUG -ti --net=host -e JAVA_OPTS="-Dhazelcast.rest.enabled=true" -e LOG__dk_dbc_ess_service_Formatting=TRACE -v $PWD/target/ess-payara-service.war:/opt/payara5/deployments/ess-payara-service.war -p 8080:8080 docker-de.artifacts.dbccloud.dk/ess-payara-service-1.0:devel`
