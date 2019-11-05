package dk.dbc.ess.service;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import dk.dbc.ess.service.response.EssResponse;
import dk.dbc.ess.service.response.HowRuResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class EssServiceIT {
//    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("config_test.yaml"); // fixme
    private EssConfiguration conf;
    private Client client;
    private ExternalSearchService essService;

    private final int readTimeout = 1500;              // ms
    private final int fixedDelay  = readTimeout + 500; // ms

    @Rule
    public WireMockRule wireMockRule = ((Supplier<WireMockRule>)()-> {
        WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
        wireMockRule.start();
        return wireMockRule;
    }).get();

    @Before
    public void setUp() {
        EssConfiguration conf = new EssConfiguration(
                "META_PROXY_URL=http://localhost:" + wireMockRule.port() + "/",
                "OPEN_FORMAT_URL=http://localhost:" + wireMockRule.port() + "/",
                "BASES=libris,bibsys"
        );
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
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
        Response response = client.target(
                String.format("http://localhost:%d/api/rpn/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error", e.getTagName());
        assertEquals("message", e.getFirstChild().getNodeName());
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
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

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=track1234", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
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

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
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

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();

        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
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

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();

        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
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

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();

        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());

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
            Response response = client.target(
                    String.format("http://localhost:%d/api/?base=%s&query=horse&start=&rows=1&format=netpunkt_standard",
                            wireMockRule.port(), base))
                    .request()
                    .get();
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    public void externalBaseTimeoutTest() {
        /* Testing Read-timeout for different bases
         */
        stubFor(get(urlMatching("/.*?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));

        // Test all configured external search systems
        List<String> bases = conf.getBases();
        for( String base: bases) {
            Response response = client.target(
                    String.format("http://localhost:%d/api/?base=%s&query=horse&start=&rows=1&format=netpunkt_standard",
                            wireMockRule.port(), base))
                    .request()
                    .get();
            assertEquals(500, response.getStatus());
        }
    }

    /*@Test
    public void formatterNotValidTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // TODO open format should fail, and when we know how it errors, we should error appropriately
        // TODO needs open format stub...
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=XYZ&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(500, response.getStatus());
    }*/

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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=XYZ&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", wireMockRule.port()))
                .request()
                .get();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void maxPageSizeProperMetaRequest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=5"))
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&format=netpunkt_standard&rows=100", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5, r.records.size());
    }

    @Test
    public void maxPageSizeDefaultToMax() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=5"))
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
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&format=netpunkt_standard", wireMockRule.port()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5, r.records.size());
    }

    @Test
    public void howRUAllOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Gr8")));

        Response response = client.target(
                String.format("http://localhost:%d/api/howru", wireMockRule.port()))
                .request()
                .get();
        assertEquals(200, response.getStatus());
        HowRuResponse result = response.readEntity(HowRuResponse.class);

        assertTrue(result.ok);
        assertEquals(null, result.message);

    }

    @Test
    public void howRUOpensearchNotOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(500)));

        checkHealthCheck();
    }

    @Test
    public void howRUMetaproxyNotOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(404)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Gr8")));

        checkHealthCheck();
    }


    private void checkHealthCheck() {
        Response response = client.target(
                String.format("http://localhost:%d/api/howru", wireMockRule.port()))
                .request()
                .get();

        HowRuResponse result = response.readEntity(HowRuResponse.class);

        assertEquals(500, response.getStatus());
        assertFalse(result.ok);
        assertEquals("downstream error - check healthchecks on admin url", result.message);
    }

}
