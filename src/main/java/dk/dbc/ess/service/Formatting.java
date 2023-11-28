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

import dk.dbc.open.format.dto.FormatRequest;
import dk.dbc.open.format.dto.FormatResponse;
import dk.dbc.openformat.OriginalData;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXB;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Singleton
public class Formatting {
    private static final Logger log = LoggerFactory.getLogger(Formatting.class);

    @Inject
    public EssConfiguration configuration;

    private final String openFormatUrl;
    private final Client client;

    public Formatting() {
        if (configuration != null) {
            this.openFormatUrl = configuration.getOpenFormatUrl();
            this.client = configuration.getClient();
        }
        else {
            Map<String, String> env = System.getenv();
            if (env.containsKey("OPEN_FORMAT_URL")) {
                this.openFormatUrl = env.get("OPEN_FORMAT_URL");
            }
            else {
                this.openFormatUrl = null;
            }
            this.client = ClientBuilder.newBuilder().build();
        }
    }

    // for integration test
    public Formatting(EssConfiguration conf) {
        this.openFormatUrl = conf.getOpenFormatUrl();
        this.client = conf.getClient();
    }

    public static final ErrorDocument ERROR_DOCUMENT = new ErrorDocument();

    @Timed(name = "call-openformat")
    static Response InvokeUrl(Client client, String openFormatUrl, FormatRequest request) {
        Invocation invocation = client.target(openFormatUrl)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .buildPost(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));
        return invocation.invoke();
    }

    private Element format(Element in, String outputFormat, String id, String trackingId) {
        try {
            final OriginalData originalData = new OriginalData();
            originalData.setIdentifier(id);
            originalData.setAny(in);

            final FormatRequest formatRequest = getFormatRequest(outputFormat, originalData, trackingId);

            Response response = InvokeUrl(client, openFormatUrl, formatRequest);
            Response.StatusType status = response.getStatusInfo();
            log.debug("status = {}", status);

            if (status.equals(Response.Status.OK)) {
                final FormatResponse formatResponse = response.readEntity(FormatResponse.class);
                final FormatResponse.Formatted formattedObject = formatResponse.getObjects().get(0).get(outputFormat);

                final String error = formattedObject.getError();
                if (error != null) {
                    log.error("Openformat responded with: {} for: {}", error, trackingId);
                    return error("Formatting error - content error: " + error);
                }

                return getFormattedElement(formattedObject.getFormatted());
            } else {
                log.error("OpenFormat responded http status: {} for: {}", status, trackingId);
                return error("Formatting error - server error: status=" + status);
            }
        } catch (Exception ex) {
            log.error("Error processing record: {} for: {}", ex.getClass(), trackingId, ex);
        }
        return ERROR_DOCUMENT.getDocument("Internal Server Error");
    }

    private FormatRequest getFormatRequest(String displayFormat, OriginalData input, String trackingId) {
        final StringWriter sw = new StringWriter();
        JAXB.marshal(input, sw);
        String inputXmlString = sw.toString();
        log.trace("input = {}", inputXmlString);

        final FormatRequest.ObjectSource objectSource = new FormatRequest.ObjectSource();
        objectSource.setObject(inputXmlString);

        final FormatRequest formatRequest = new FormatRequest();
        formatRequest.setFormats(Set.of(displayFormat));
        formatRequest.setTrackingId(trackingId);
        formatRequest.setObjects(List.of(objectSource));

        return formatRequest;
    }

    private Element getFormattedElement(String formatted) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        final Document parsedDocument = documentBuilder.parse(new InputSource(new StringReader(formatted)));
        final Element parsedElement = parsedDocument.getDocumentElement();
        final String format = parsedElement.getAttribute("format");

        final Document formattedDocument = documentBuilder.newDocument();
        final Element formattedElement = formattedDocument.createElementNS("http://oss.dbc.dk/ns/openformat", format);
        final NodeList parsedElementChildNodes = parsedElement.getChildNodes();
        for (int i = 0; i < parsedElementChildNodes.getLength(); i++) {
            final Node child = formattedDocument.importNode(parsedElementChildNodes.item(i), true);
            formattedElement.appendChild(child);
        }
        return formattedElement;
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
