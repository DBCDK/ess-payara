FROM docker.dbc.dk/payara5-micro:latest

LABEL \
    META_PROXY_URL="Url of metaproxy (indexdata.com) (required)" \
    OPEN_FORMAT_URL="Url of openformat service (required)" \
    FORMATS="Comma seperated list of known formats (required)" \
    MAX_PAGE_SIZE="Maximum number of documents retrieveable with a query (required)" \
    BASES="Comma seperated list of known bases (required)"

ENV JAVA_MAX_HEAP_SIZE=2G

# Unconfigurable thread-pool in payara image
RUN echo "set server.thread-pools.thread-pool.http-thread-pool.max-thread-pool-size=500" >>scripts/prebootcommandfile.txt

COPY target/${project.build.finalName}.war target/docker/app.json deployments/
