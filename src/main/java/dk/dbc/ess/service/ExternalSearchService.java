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
import dk.dbc.ess.service.usage.Usage;
import dk.dbc.ess.service.usage.UsageLogger;
import dk.dbc.sru.diagnostic.Diagnostic;
import dk.dbc.sru.sruresponse.Diagnostics;
import dk.dbc.sru.sruresponse.Record;
import dk.dbc.sru.sruresponse.RecordXMLEscapingDefinition;
import dk.dbc.sru.sruresponse.Records;
import dk.dbc.sru.sruresponse.SearchRetrieveResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@Stateless
@Path("/")
public class ExternalSearchService {
    private static final Logger log = LoggerFactory.getLogger(ExternalSearchService.class);

    @Inject
    public EssConfiguration configuration;

    @Inject
    public Formatting formatting;

    @Inject
    MetricRegistry metricRegistry;

    @EJB
    UsageLogger usageLogger;

    ExecutorService executorService;

    private Counter serverErrorsCounter;

    public ExternalSearchService() {
        this.executorService = Executors.newCachedThreadPool();
    }

    @PostConstruct
    public void init() {
        serverErrorsCounter = metricRegistry.counter("server_errors");
    }

    @GET
    @Path("rpn")
    public Response requestRPN(@QueryParam("base") @NotNull String base,
                               @QueryParam("query") @NotNull String query,
                               @QueryParam("start") Integer start,
                               @QueryParam("rows") Integer rows,
                               @QueryParam("format") @NotNull String format,
                               @QueryParam("clientId") String clientId,
                               @QueryParam("agencyId") String agencyId,
                               @QueryParam("trackingId") String trackingId) {
        return processRequest(base, query, start, rows, format, clientId, agencyId, trackingId, true);
    }

    @GET
    public Response requestCQL(@QueryParam("base") @NotNull String base,
                               @QueryParam("query") @NotNull String query,
                               @QueryParam("start") Integer start,
                               @QueryParam("rows") Integer rows,
                               @QueryParam("format") @NotNull String format,
                               @QueryParam("clientId") String clientId,
                               @QueryParam("agencyId") String agencyId,
                               @QueryParam("trackingId") String trackingId) {
        return processRequest(base, query, start, rows, format, clientId, agencyId, trackingId, false);
    }

    private Response processRequest(String base, String query, Integer start, Integer rows, String format,
                                    String clientId, String agencyId, String trackingId, boolean isRPN) {
        if (start == null) {
            start = 1;
        }
        if (rows == null || rows >= configuration.getMaxPageSize()) {
            rows = configuration.getMaxPageSize();
        }
        if (trackingId == null || trackingId.isEmpty()) {
            trackingId = UUID.randomUUID().toString();
        }
        if (!configuration.getBases().contains(base)) {
            return serverError("Unknown base requested");
        }
        log.info("base: {}; format: {}; start: {}; rows: {}; clientId: {}; agencyId: {}; trackingId: {}; query: {}; type: {}",
                base, format, start, rows, clientId, agencyId, trackingId, query, isRPN ? "rpn" : "cql");

        Usage usage = null;
        try {
            String queryParam = isRPN ? "x-pquery" : "query";
            Response response = requestSru(base, queryParam, query, start, rows);

            if (!response.getStatusInfo().equals(Response.Status.OK)) {
                log.error("Search failed with http code: " + response.getStatusInfo() + " for: " + trackingId);
                return serverError("Internal Server Error");
            }

            SearchRetrieveResponse sru = responseSru(response);

            try { // metaProxy can return a 200 OK response with error messages in it, so we check for "Diagnostics"
                Diagnostics sruDiagnostics = sru.getDiagnostics();
                List<Diagnostic> diagList = sruDiagnostics.getDiagnostics();
                StringBuilder details = new StringBuilder();
                for (Diagnostic d: diagList) {
                    details.append(d.getDetails());
                    log.error("Error encountered in SRU response (details): " + d.getDetails());
                    log.error("Error encountered in SRU response (message): " + d.getMessage());
                }
                Response.ResponseBuilder rb = Response.status(Response.Status.BAD_GATEWAY);
                rb.entity(details.toString());
                return rb.build();
            } catch (NullPointerException dnpe) { } //NOPMD
            // no diagnostics is a good thing, carry on...

            // We got a non-error/no-diagnostics response from metaproxy, so the external database was definitely hit
            usage = new Usage()
                    .withDatabaseId(base)
                    .withClientId(clientId)
                    .withAgencyId(agencyId);

            return buildResponse(sru, format, base + ":", trackingId, usage);

        } catch (Exception ex) {
            log.error("Error Processing Response: " + ex.getMessage() + " for: " + trackingId);
            log.debug("Error Processing Response:", ex);
        } finally {
            if (usage != null) {
                try {
                    usageLogger.log(usage);
                } catch (RuntimeException e) {
                    log.error("Unable to update usage log", e);
                    serverErrorsCounter.inc();
                }
            }
        }
        return serverError("Internal Server Error");
    }

    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    Response buildResponse(SearchRetrieveResponse sru, String outputFormat, String idPrefix, String trackingId,
                           Usage usage)
        throws InterruptedException, ExecutionException {
        final String controlField = "controlfield";
        final String zeroZeroOne = "001";
        EssResponse essResponse = new EssResponse();
        essResponse.records = new ArrayList<>();
        essResponse.trackingId = trackingId;
        Records recs = null;

        try {
            Long hits = sru.getNumberOfRecords();
            log.debug("Number of records read was: " + hits.toString());
            essResponse.hits = sru.getNumberOfRecords();
            recs = sru.getRecords();
        } catch (NullPointerException npe) {
            log.error("Error reading record data from SearchRetrieveResponse", npe);
            Response.ResponseBuilder rb = Response.status(Response.Status.BAD_GATEWAY);
            rb.entity("Error extracting records from MetaProxy response");
            return rb.build();
        }

        if (recs != null) {
            List<Record> recordList = recs.getRecords();
            List<Future<Element>> futures = new ArrayList<>(recordList.size());
            usage.withRecordCount(recordList.size());
            log.debug("Sending records to OpenFormat...");
            for (Record record : recordList) {
                Future<Element> future;
                RecordXMLEscapingDefinition esc = record.getRecordXMLEscaping();
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
                                            remoteId = idPrefix + id.getNodeValue().trim();
                                        }
                                        break;
                                    }
                                }
                            }
                            if (remoteId == null) {
                                remoteId = idPrefix + UUID.randomUUID().toString();
                            }
                            future = executorService.submit(formatting.formattingCall(e, outputFormat, remoteId, trackingId));
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
            log.debug("All records returned from OpenFormat...");
        }
        return Response.ok(essResponse, MediaType.APPLICATION_XML_TYPE).build();
    }

    @Timed(name = "call-meta-proxy")
    Response requestSru(String base, String queryParam, String query, Integer start, Integer stepValue)
            throws Exception {
        Invocation invocation = configuration.getClient()
                .target(configuration.getMetaProxyUrl())
                .path(base)
                .queryParam(queryParam, query)
                .queryParam("startRecord", start)
                .queryParam("maximumRecords", stepValue)
                .request(MediaType.APPLICATION_XML_TYPE)
                .buildGet();
        log.debug("Sending request to MetaProxy...");
        Response res =  invocation.invoke();
        log.debug("Response from MetaProxy was: " + res);
        return res;
    }

    @Timed(name = "read-response-entity")
    SearchRetrieveResponse responseSru(Response response) throws Exception {
        try {
            return response.readEntity(SearchRetrieveResponse.class);
        }
        catch (ProcessingException pe) {
            log.error("Error when reading entity SearchRetrieveResponse from response");
            throw pe;
        }
    }

    Response serverError(String message) {
        return Response.serverError().entity(message).build();
    }

}
