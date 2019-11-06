# external-search-system (Payara)

Denne service er en migreret version af "external-search-system" (<https://github.com/DBCDK/external-search-system>) til Payara.
Det er en service som faciliterer universal search, 
hvor resultater kan returneres i formater understøttet i Open Format.

## Kørsel

Servicen er et Payara projekt med config filen i `service/src/main/resources/config.yaml`. 
Følgende environment variabler er påkrævede:
 - `BASES`: Hvilke baser som servicen tillader at efterspørge meta proxyen med til universal search.
 - `META_PROXY_URL`: Endpoint for meta proxy til universal search.
 - `OPEN_FORMAT_URL`: Url til en open format service. 
 
Et docker image kan også bygges ved at køre `mvn clean package`.

## Query parametre
 - `base`: Parameter der beskriver hvilken base der søges ned i. Exsempler inkluderer: `libris` og `bibsys`.
 - `query`: Efterspørgsel, kan formuleres i cql.
 - `format`: Ønsket output format. Gives direkte videre til Open Format.
 - `rows`: Antallet af rækker der ønskes returneret. Benyttes til paging.
 - `start`: Offset for de ønskede resultater, benyttes sammen med `rows` til paging. Defaulter til 0.
 
Eksempel URL:
`http://host:port/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=`
 
## Noter

 - Få openformat endpoint der kan ikke fejler, deploy ess med denne
