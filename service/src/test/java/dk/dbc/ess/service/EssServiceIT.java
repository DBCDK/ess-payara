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

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import dk.dbc.ess.service.response.EssResponse;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class EssServiceIT {

    private EssConfiguration conf;
    private Client client;
    private ExternalSearchService essService;

    private final int readTimeout = 1500;              // ms
    private final int fixedDelay  = readTimeout + 500; // ms

    public EssServiceIT() {}
    private final int wireMockPort = Integer.parseInt(System.getProperty("wiremock.port"));

    @Rule
    public WireMockRule wireMockRule = ((Supplier<WireMockRule>)()-> {
        WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(wireMockPort));
        wireMockRule.start();
        return wireMockRule;
    }).get();

    @Before
    public void setUp() throws Exception {
        conf = new EssConfiguration(
                "BASES=libris,bibsys",
                "META_PROXY_URL=" + "http://localhost:" + wireMockRule.port() + "/",
                "OPEN_FORMAT_URL="+ "http://localhost:" + wireMockRule.port() + "/",
                "MAX_PAGE_SIZE=5"
        ) {
            @Override
            protected Client getClient() {
                return JerseyClientBuilder.newBuilder().build();
            }
        };
        client = conf.getClient();
        essService = new ExternalSearchService();
        essService.configuration = conf;
        essService.formatting = new Formatting(conf);
    }

    @Test
    public void essServiceBaseFoundTest() throws Exception {
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));

        Response result = essService.requestSru("bibsys", "query", "horse", 1, 1);
        assertEquals(200, result.getStatus());
    }

    @Test
    public void essServiceBaseInternalErrorTest() throws Exception {
        stubFor(get(urlMatching("/bibsys.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("")));

        Response result = essService.requestSru("bibsys", "query", "horse", 1, 1);
        assertEquals(500, result.getStatus());
    }

    @Test
    public void essServiceBaseNotFoundTest() throws Exception {
        stubFor(get(urlMatching(".*dog.*"))
                .willReturn(aResponse()
                        .withStatus(404)));

        Response result = essService.requestSru("bibsys", "query", "dog", 1, 1);
        assertEquals(404, result.getStatus());
    }

    @Test
    public void bibsysRespondingOKTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertNotEquals("error",e.getTagName());
        assertNotEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void bibsysRPNRespondingOKTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?x-pquery=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestRPN("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertNotEquals("error",e.getTagName());
        assertNotEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormat404Test() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Ensures request to open format is a proper format request, and returns a 404
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")
                        .withStatus(404)));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("#text",e.getFirstChild().getNodeName()); // todo: had to change this here and below!
    }

    @Test
    public void openFormatConnectionFailed() {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withHeader("Connection","Keep-Alive")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Ensures request to open format is a proper format request, and makes a connection reset
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error", e.getTagName());
        assertEquals("#text", e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatConnectionTimeout(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with delay that would trigger a socket timeout response
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));

        // In this response, open format response is delayed by 2s, making the socket time out
        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("netpunkt_standard",e.getTagName()); // todo: had to change this...?
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    // TODO Maybe move this test over to positive tests?
    @Test
    public void openFormatTrakcingIdPassed(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct tracking ID is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'trackingId']/text()",equalTo("track1234")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "track1234");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        assertEquals("netpunkt_standard",e.getTagName()); // todo: there was nothing here before...
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatEmptyResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withFault(Fault.EMPTY_RESPONSE)));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatFormatErrorResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_error_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    @Test
    public void baseSearchGarbledEscapingErrorResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_garbled_escaping_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    @Test
    public void baseSearchDuplicateRecordsErrorResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_duplicate_record_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                // Check the correct format is requested
                .withRequestBody(matchingXPath("/*[local-name() = 'formatRequest']/*[local-name() = 'outputFormat']/text()",equalTo("netpunkt_standard")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 1, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("#text",e.getFirstChild().getNodeName());
    }

    @Test
    public void externalBaseNotReturningOKTest() throws Exception {
        givenThat(get(urlMatching(".*query=horse.*"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("")));

        // Test all configured external search systems
        List<String> bases = conf.getBases();
        for( String base: bases) {
            Response response = essService.requestCQL(base, "horse", 1, 1, "netpunkt_standard", "");
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    public void externalBaseTimeoutTest() {
        // Testing Read-timeout for different bases
        stubFor(get(urlMatching("/.*?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));

        // Test all configured external search systems
        List<String> bases = conf.getBases();
        for( String base: bases) {
            Response response = essService.requestCQL(base, "horse", 1, 1, "netpunkt_standard", "");
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    public void baseNotValidTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // TODO open format should fail, and when we know how it errors, we should error appropriately
        // TODO needs open format stub...
        Response response = essService.requestCQL("XYZ", "horse", 1, 1, "netpunkt_standard", "");
        assertEquals(500, response.getStatus());
    }

    @Test
    public void maxPageSizeProperMetaRequest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=5")) // todo: had to alter this!
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("response_max_page_5.xml")));
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", 1, 100, "netpunkt_standard", "");
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5, r.records.size());
    }

    @Test
    public void maxPageSizeDefaultToMax() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=" + conf.getMaxPageSize()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("response_max_page_5.xml")));
        stubFor(post(urlEqualTo("/"))
                // Check root is format request with correct namespace
                .withRequestBody(matchingXPath("//fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));

        Response response = essService.requestCQL("bibsys", "horse", null, null, "netpunkt_standard", null);
        EssResponse r = (EssResponse) response.getEntity();
        assertEquals(5, r.records.size());
    }

}
