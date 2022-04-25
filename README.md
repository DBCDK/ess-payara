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
Først bygges `.war` fil med  `mvn clean package` og derefter `docker build -t ess-payara -f target/docker/Dockerfile .` (inklusiv det sidste punktum).
 
 ## Run docker image
 
Her er et eksempel der viser hvordan docker imaget kan køres.
 Here is an example of a command to run the image - the values of the environment vars come from gitlab:
 
 `docker run -e "META_PROXY_URL=http://pz2-p01.dbc.dk:9001/" -e "OPEN_FORMAT_URL=http://openformat-php-master.frontend-prod.svc.cloud.dbc.dk/server.php" -e "BASES=bibsys,OLUCWorldCat,ArticleFirst" -ti --net=host -e JAVA_OPTS="-Dhazelcast.rest.enabled=true" ess-payara`
 
Endnu et eksempel, hvis du har brug for mere output til debug (i dette tilfælde fra klassen `Formatting`):

`docker run -e "META_PROXY_URL=http://pz2-p01.dbc.dk:9001/" -e "OPEN_FORMAT_URL=http://openformat-php-master.frontend-prod.svc.cloud.dbc.dk/server.php" -e "BASES=bibsys,OLUCWorldCat,ArticleFirst" -e LOG__dk_dbc=DEBUG -ti --net=host -e JAVA_OPTS="-Dhazelcast.rest.enabled=true" -e LOG__dk_dbc_ess_service_Formatting=TRACE -v $PWD/target/ess-payara-service.war:/opt/payara5/deployments/ess-payara-service.war -p 8080:8080 ess-payara`

## Noter
 - Få OpenFormat endpoint der ikke fejler, deploy External Search Service med denne.
