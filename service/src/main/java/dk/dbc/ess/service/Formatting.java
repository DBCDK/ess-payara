/*
 * Copyright (C) 2017 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-ess-service
 *
 * dbc-ess-service is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-ess-service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dk.dbc.ess.service;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.dbc.openformat.FormatRequest;
import dk.dbc.openformat.FormatResponse;
import dk.dbc.openformat.OriginalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.concurrent.Callable;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
public class Formatting {
    private static final ObjectMapper O = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(Formatting.class);

    public static final ErrorDocument ERROR_DOCUMENT = new ErrorDocument();

    private final String openFormatUrl;
    private final Client client;
    private final Timer timerFormatRequest;

    public Formatting(Settings settings, MetricRegistry metrics, Client client) {
        openFormatUrl = settings.getOpenFormatUrl();
        this.client = client;
        this.timerFormatRequest = mkTimer(metrics, "formatRequest");

    }

    private Timer mkTimer(MetricRegistry metrics, String name) {
        return metrics.timer(getClass().getCanonicalName() + "#" + name);
    }

    private Element format(Element in, String outputFormat, String id, String trackingId) {
        try {
            FormatRequest request = new FormatRequest();

            request.setOutputFormat(outputFormat);
            request.setTrackingId(trackingId);
            OriginalData originalData = new OriginalData();
            originalData.setIdentifier(id);
            originalData.setAny(in);
            request.getOriginalDatas().add(originalData);
            Invocation invocation = client.target(openFormatUrl)
                    .request(MediaType.APPLICATION_XML_TYPE)
                    .buildPost(Entity.entity(request, MediaType.APPLICATION_XML_TYPE));

            if (log.isTraceEnabled()) {
                StringWriter sw = new StringWriter();
                try {
                    JAXBContext carContext = JAXBContext.newInstance(FormatRequest.class);
                    Marshaller carMarshaller = carContext.createMarshaller();
                    carMarshaller.marshal(request, sw);
                    log.trace("request = {}", sw);
                } catch (JAXBException e) {
                    log.trace("Cannot convert using JAXB", e);
                }
            }

            Response response = timerFormatRequest.time(() -> invocation.invoke());
            Response.StatusType status = response.getStatusInfo();

            log.debug("status = {}", status);

            if (status.equals(Response.Status.OK)) {
                FormatResponse formatted = response.readEntity(FormatResponse.class);
                if (log.isTraceEnabled()) {
                    StringWriter sw = new StringWriter();
                    try {
                        JAXBContext carContext = JAXBContext.newInstance(FormatResponse.class);
                        Marshaller carMarshaller = carContext.createMarshaller();
                        carMarshaller.marshal(formatted, sw);
                        log.trace("response = {}", sw);
                    } catch (JAXBException e) {
                        log.trace("Cannot convert using JAXB", e);
                    }
                }
                String error = formatted.getError();
                if (error != null) {
                    log.error("Openformat responded with: " + error + " for: " + trackingId);
                    return error("Formatting error - content error");
                }
                return formatted.getAny();
            } else {
                log.error("OpenFormat responded http status: " + status + " for: " + trackingId);
                return error("Formatting error - server error");
            }
        } catch (Exception ex) {
            log.error("Error processing record: " + ex.getClass().getName() + " " + ex.getMessage() + " for: " + trackingId);
            log.debug("Error processing record:", ex);
        }
        return ERROR_DOCUMENT.getDocument("Internal Server Error");
    }

    private Element error(String message) {
        return ERROR_DOCUMENT.getDocument(message);
    }

    public Callable<Element> formattingCall(Element in, String outputFormat, String id, String trackingId) {
        return () -> format(in, outputFormat, id, trackingId);
    }

    public Callable<Element> formattingError(String message) {
        return new FormattingError(message);

    }

    public class FormattingError implements Callable<Element> {

        private final String message;

        public FormattingError(String message) {
            this.message = message;
        }

        @Override
        public Element call() throws Exception {
            return error(message);
        }
    }

    public static class ErrorDocument {

        private Document doc;
        private Element node;
        private int[] pos;

        public ErrorDocument() {
            try (InputStream is = Formatting.class.getResourceAsStream("/error_document.xml")) {
                this.doc = XmlTools.newDocumentBuilder().parse(is);
                this.node = doc.getDocumentElement();
                LinkedList<Integer> list = findMessagePath(node);
                if (list == null) {
                    throw new RuntimeException("Unable to find message node");
                }
                this.pos = list.stream().mapToInt(i -> i).toArray();
            } catch (SAXException | IOException ex) {
                throw new RuntimeException("Error creating error document", ex);
            }
        }

        private static LinkedList<Integer> findMessagePath(Node n) {
            int pos = 0;
            for (Node child = n.getFirstChild() ; child != null ; child = child.getNextSibling(), pos++) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    NamedNodeMap attrs = child.getAttributes();
                    if (attrs != null) {
                        Node id = attrs.getNamedItem("id");
                        if (id != null && id.getNodeValue().equals("message")) {
                            attrs.removeNamedItem("id");
                            LinkedList<Integer> list = new LinkedList<>();
                            list.addFirst(pos);
                            return list;
                        }
                    }
                    LinkedList<Integer> list = findMessagePath(child);
                    if (list != null) {
                        list.addFirst(pos);
                        return list;
                    }
                }
            }
            return null;
        }

        public synchronized Element getDocument(String content) {
            Node copy = node.cloneNode(true);
            Node msg = copy;
            for (int index : pos) {
                msg = msg.getChildNodes().item(index);
            }
            msg.appendChild(doc.createTextNode(content));
            return (Element) copy;
        }
    }
}
