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
import dk.dbc.ess.service.usage.UsageLogger;
import dk.dbc.sru.sruresponse.SearchRetrieveResponse;
import dk.dbc.xmldiff.XmlDiff;
import dk.dbc.xmldiff.XmlDiffTextWriter;
import dk.dbc.xmldiff.XmlDiffWriter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static javax.xml.bind.JAXBContext.newInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class EssServiceTest {
    private final XmlDiff diff;
    private final Response responseOk;
    private final Response responseError;

    public EssServiceTest() {
        this.diff = XmlDiff.builder()
                .indent(2)
                .normalize(true)
                .strip(true)
                .trim(true)
                .build();

        responseOk = mock(Response.class);
        doReturn(Response.Status.OK).when(responseOk).getStatusInfo();
        responseError = mock(Response.class);
        doReturn(Response.Status.INTERNAL_SERVER_ERROR).when(responseError).getStatusInfo();
    }

    @Test
    void testCQLRequestSuccess() throws Exception {
        ExternalSearchService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.requestCQL("base", "", 0, 0, "format", "", "", "T");
        EssResponse entity = (EssResponse) resp.getEntity();
        boolean equivalent = compare("/sru/expected_success.xml", writeXmlObject(entity));
        assertTrue("Documents are expected to be equivalent: ", equivalent);
    }

    @Test
    void testRPNRequestSuccess() throws Exception {
        ExternalSearchService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.requestRPN("base", "", 0, 0, "format", "", "", "T");
        EssResponse entity = (EssResponse) resp.getEntity();
        boolean equivalent = compare("/sru/expected_success.xml", writeXmlObject(entity));
        assertTrue("Documents are expected to be equivalent: ", equivalent);
    }

    @Test
    void testRequestBadBase() throws Exception {
        ExternalSearchService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.requestCQL("badbase", "", 0, 0, "format", null, null, null);
        assertNotEquals("Not success", 200, resp == null ? -1 : resp.getStatus());
    }

    @Test
    void testRequestBadEscape() throws Exception {
        ExternalSearchService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response_bad_escape.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.requestCQL("base", "", 0, 0, "format", "", "", "T");
        assertEquals("Success", 200, resp.getStatus());
        EssResponse entity = (EssResponse) resp.getEntity();
        String actual = writeXmlObject(entity);
        boolean equivalent = compare("/sru/expected_bad_escape.xml", actual);
        assertTrue("Documents are expected to be equivalent: ", equivalent);
    }

    protected static ExternalSearchService mockService(String bases, String formats, String... docs) throws ExecutionException, InterruptedException {
        EssConfiguration conf = new EssConfiguration(
                "BASES=libris,bibsys" + (StringUtils.isBlank(bases) ? "" : ","+bases),
                "META_PROXY_URL=whatever",
                "OPEN_FORMAT_URL=notUsed",
                "MAX_PAGE_SIZE=5"
        );
        ExternalSearchService essService = mock(ExternalSearchService.class);
        essService.executorService = mockExecutorService();
        essService.formatting = makeFormatting(docs);
        essService.usageLogger = mock(UsageLogger.class);
        essService.metricRegistry = mock(MetricRegistry.class);
        essService.configuration = conf;
        doCallRealMethod().when(essService).requestCQL(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
        doCallRealMethod().when(essService).requestRPN(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
        doCallRealMethod().when(essService).serverError(anyString());
        doCallRealMethod().when(essService).buildResponse(any(SearchRetrieveResponse.class), anyString(), anyString(), anyString(), any());
        return essService;
    }

    private boolean compare(String expected, String actual) throws SAXException, IOException, XPathExpressionException {
        XmlDiffWriter writer;
        if (System.getProperty("test") == null) {
            writer = new XmlDiffTextWriter("\u001b[4m", "\u001b[0m", "\u001b[1m", "\u001b[0m", "\u001b[3m", "\u001b[0m");
        } else {
            writer = new XmlDiffTextWriter("\u00bb-", "-\u00ab", "\u00bb+", "+\u00ab", "\u00bb?", "?\u00ab");
        }
        try (InputStream left = getClass().getResourceAsStream(expected);
             InputStream right = new ByteArrayInputStream(actual.getBytes(StandardCharsets.UTF_8))) {
            boolean equivalent = diff.compare(left, right, writer);
            System.out.println(writer);
            return equivalent;
        }
    }

    protected static Formatting makeFormatting(String... xmls) {
        Formatting formatting = mock(Formatting.class);
        doCallRealMethod().when(formatting).formattingError(anyString());
        OngoingStubbing<Callable<Element>> stub = when(formatting.formattingCall(any(Element.class), anyString(), anyString(), anyString()));
        for (String xml : xmls) {
            stub = stub.then(i -> (Callable<Element>) ()-> stringToXMLObject(xml));
        }
        return formatting;
    }

    protected static ExecutorService mockExecutorService() {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Callable.class)))
                .thenAnswer(i -> {
                    Callable<?> callable = (Callable<?>) i.getArguments()[0];
                    Object ret = callable.call();
                    Future future = mock(Future.class);
                    doReturn(ret).when(future).get();
                    return future;
                });
        return executor;
    }

    private static Element stringToXMLObject(String xml) {
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return XmlTools.newDocumentBuilder().parse(is).getDocumentElement();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private <T> T readXMLObject(Class<? extends T> t, String resource) throws JAXBException {
        JAXBContext jaxbContext = newInstance(t);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (T) unmarshaller.unmarshal(getClass().getResource(resource));
    }

    private static <T> String writeXmlObject(T obj) throws JAXBException {
        JAXBContext jaxbContext = newInstance(obj.getClass());
        Marshaller marshaller = jaxbContext.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(obj, writer);
        return writer.toString();
    }

}
