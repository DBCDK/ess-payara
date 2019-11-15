/*
 * Copyright (C) 2019 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-ess-payara
 *
 * dbc-ess-payara is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-ess-payara is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.ess.service;

import dk.dbc.ess.service.response.EssResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import dk.dbc.sru.sruresponse.Record;
import dk.dbc.sru.sruresponse.Records;
import dk.dbc.sru.sruresponse.SearchRetrieveResponse;
import dk.dbc.sru.sruresponse.RecordXMLEscapingDefinition;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class ExternalSearchService {
    private static final Logger log = LoggerFactory.getLogger(ExternalSearchService.class);

    Client client;
    Collection<String> knownBases;
    private String sruTargetUrl;
    Formatting formatting;

    ExecutorService executorService;
    Timer timerSruRequest;
    Timer timerSruReadResponse;
    Timer timerRequest;
    private int maxPageSize;

    public ExternalSearchService(Settings settings, MetricRegistry metrics) {
        this.client = settings.getClientBuilder().build();
        this.knownBases = settings.getBases();
        this.sruTargetUrl = settings.getMetaProxyUrl();
        this.maxPageSize = settings.getMaxPageSize();
        this.executorService = Executors.newCachedThreadPool();
        this.formatting = new Formatting(settings, metrics, client);
        this.timerSruRequest = makeTimer(metrics, "sruRequest");
        this.timerSruReadResponse = makeTimer(metrics, "sruReadResponse");
        this.timerRequest = makeTimer(metrics, "Request");
    }

    @GET
    @Path("rpn/")
    public Response requestRPN(@QueryParam("base") @NotNull String base,
                               @QueryParam("query") @NotNull String query,
                               @QueryParam("start") Integer start,
                               @QueryParam("rows") Integer rows,
                               @QueryParam("format") @NotNull String format,
                               @QueryParam("trackingId") String trackingId) {
        return processRequest(base, query, start, rows, format, trackingId, true);
    }

    @GET
    public Response requestCQL(@QueryParam("base") @NotNull String base,
                               @QueryParam("query") @NotNull String query,
                               @QueryParam("start") Integer start,
                               @QueryParam("rows") Integer rows,
                               @QueryParam("format") @NotNull String format,
                               @QueryParam("trackingId") String trackingId) {
        return processRequest(base, query, start, rows, format, trackingId, false);
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

        try (Timer.Context ignored = timerRequest.time()) {
            String queryParam = isRPN ? "x-pquery" : "query";
            Response response = requestSru(base, queryParam, query, start, rows);

            if (!response.getStatusInfo().equals(Response.Status.OK)) {
                log.error("Search failed with http code: " + response.getStatusInfo() + " for: " + trackingId);
                return serverError("Internal Server Error");
            }

            SearchRetrieveResponse sru = responseSru(response);

            return buildResponse(sru, format, "base: ", trackingId);

        } catch (Exception ex) {
            log.error("Error Processing Response: " + ex.getMessage() + " for: " + trackingId);
            log.debug("Error Processing Response:", ex);
        }
        return serverError("Internal Server Error");
    }

    Response buildResponse(SearchRetrieveResponse sru, String output, String idPrefix, String trackingId)
        throws InterruptedException, ExecutionException {
        final String controlField = "controlfield";
        final String zeroZeroOne = "001";

        EssResponse essResponse = new EssResponse();
        essResponse.hits = sru.getNumberOfRecords();
        essResponse.records = new ArrayList<>();
        essResponse.trackingId = trackingId;
        Records recs = sru.getRecords();
        if (recs != null) {
            List<Record> recordList = recs.getRecords();
            List<Future<Element>> futures = new ArrayList<>(recordList.size());
            for (Record record : recordList) {
                Future<Element> future;
                RecordXMLEscapingDefinition esc = record.getRecordXMLEscaping();
                log.debug("esc: " + esc);
                if (esc != RecordXMLEscapingDefinition.XML) {
                    log.error("Expected xml escaped record in response, got: " + esc);
                    future = executorService.submit(formatting.formattingError("Internal Server Error"));
                }
                else {
                    List<Object> content = record.getRecordData().getContent();
                    if (content.size() == 1) {
                        Object obj = content.get(0);
                        if (obj instanceof  Element) {
                            String remoteId = null;
                            Element e = (Element) obj;
                            for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
                                if (child.getNodeType() == Node.ELEMENT_NODE && controlField.equals(child.getLocalName())) {
                                    NamedNodeMap attributes = child.getAttributes();
                                    Node tag = attributes.getNamedItem("tag");
                                    if (tag != null && zeroZeroOne.equals(tag.getNodeValue())) {
                                        Node id = child.getFirstChild();
                                        if (id.getNodeType() == Node.TEXT_NODE) {
                                            remoteId = idPrefix + id.getNodeValue();
                                        }
                                        break;
                                    }
                                }
                            }
                            if (remoteId == null) {
                                remoteId = idPrefix + UUID.randomUUID().toString();
                            }
                            future = executorService.submit(formatting.formattingCall(e, output, remoteId, trackingId));
                        }
                        else {
                            log.error("Not of type XML: " + obj.getClass().getCanonicalName() + ". This should not happen.");
                            future = executorService.submit(formatting.formattingError("Internal Server Error"));
                        }
                    }
                    else {
                        log.error("Expected 1 record in response, but got " + content.size());
                        log.debug("Types: ");
                        for (Object o : content) {
                            log.debug(o.getClass().getCanonicalName());
                        }
                        future = executorService.submit(formatting.formattingError("Internal Server Error"));
                    }
                }
                futures.add(future);
            }
            for (Future<Element> f : futures) {
                essResponse.records.add(f.get());
            }
        }
        return Response.ok(essResponse, MediaType.APPLICATION_XML_TYPE).build();
    }

    Response requestSru(String base, String queryParam, String query, Integer start, Integer stepValue)
            throws Exception {
        Invocation invocation = client
                .target(sruTargetUrl)
                .path(base)
                .queryParam(queryParam, query)
                .queryParam("startRecord", start)
                .queryParam("maximumRecords", stepValue)
                .request(MediaType.APPLICATION_XML_TYPE)
                .buildGet();
        return timerSruRequest.time(invocation::invoke);
    }

    SearchRetrieveResponse responseSru(Response response) throws  Exception {
        return  timerSruReadResponse.time(() -> response.readEntity(SearchRetrieveResponse.class));
    }

    private Timer makeTimer(MetricRegistry metricRegistry, String name) {
        return metricRegistry.timer(getClass().getCanonicalName() + "#" + name);
    }

    Response serverError(String message) {
        return Response.serverError().entity(message).build();
    }

}
