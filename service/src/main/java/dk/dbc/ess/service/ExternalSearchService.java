package dk.dbc.ess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;


public class ExternalSearchService {
    private static final Logger log = LoggerFactory.getLogger(ExternalSearchService.class);

    Client client;
    Collection<String> knownBases;
    String sruTargetUrl;
    Formatting formatting;

    ExecutorService executorService;
    Timer timerSruRequest;
    Timer timerSruReadResponse;
    Timer timerRequest;
    int maxPageSize;

    public ExternalSearchService(Settings settings, MetricRegistry metrics, Client client) { // todo: metrics stuff
        this.client = client;

        this.knownBases = settings.getBases();
        this.sruTargetUrl = settings.getMetaProxyUrl();
        this.maxPageSize = settings.getMaxPageSize();

        this.executorService = Executors.newCachedThreadPool();
        this.formatting = new Formatting(settings, metrics, client);

        this.timerSruRequest = makeTimer(metrics, "sruRequest");
        this.timerSruReadResponse = makeTimer(metrics, "sruReadResponse");
        this.timerRequest = makeTimer(metrics, "Request");
    }

    private Timer makeTimer(MetricRegistry metricRegistry, String name) {
        return metricRegistry.timer(getClass().getCanonicalName() + "#" + name);
    }

    private Response processRequest(String base, String query, Integer start, Integer rows, String format, String trackingId, boolean isRPN) {
        if (start == null) {
            start = 1;
        }
        if (rows == null || rows >= maxPageSize) {
            rows = maxPageSize;
        }
        if (trackingId == null || trackingId.isEmpty()) {
            trackingId = UUID.randomUUID().toString();
        }
        if (!knownBases.contains(base)) {
            return serverError("Unknown base requested");
        }
        log.info("base: " + base + "; format: " + format +
                "; start: " + start + "; rows: " + rows +
                "; trackingId: " + trackingId + "; query: " + query + "; type: " + (isRPN ? "rpn" : "cql"));

        try (Timer.Context timer = timerRequest.time()) {
            String queryParam = isRPN ? "x-pqueryy" : "query";
            Response response = requestSru(base, queryParam, query, start, rows);

            if (!response.getStatusInfo().equals(Response.Status.OK)) {
                log.error("Search failed with http code: " + response.getStatusInfo() + " for: " + trackingId);
                return serverError("Internal Server Error");
            }


        } catch (Exception ex) {
            log.error("Error Processing Response: " + ex.getMessage() + " for: " + trackingId);
            log.debug("Error Processing Response:", ex);
        }
    }


    private Response requestSru(String base, String queryParam, String query, Integer start, Integer stepValue)
            throws Exception {
        Invocation invocation = client
                .target(sruTargetUrl)
                .path(base)
                .queryParam(queryParam, query)
                .queryParam("startRecord", start)
                .queryParam("maximumRecords", stepValue)
                .request(MediaType.APPLICATION_XML_TYPE)
                .buildGet();
        return timerSruRequest.time(() -> invocation.invoke());
    }

    private Response serverError(String message) {
        return Response.serverError().entity(message).build();
    }

}
