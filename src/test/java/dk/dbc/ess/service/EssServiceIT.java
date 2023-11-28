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
import dk.dbc.ess.service.response.EssResponse;
import dk.dbc.ess.service.usage.Usage;
import dk.dbc.httpclient.HttpGet;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class EssServiceIT extends ContainerTestBase {

    private final int readTimeout = 1500;              // ms
    private final int fixedDelay  = readTimeout + 500; // ms

    @Test
    void externalDatabaseCQL_OK() {
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#externalDatabaseCQL_OK";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("netpunkt_standard"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void externalDatabaseRPN_OK() {
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?x-pquery=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#externalDatabaseRPN_OK";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api", "rpn")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("netpunkt_standard"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void metaproxyInternalServerError() {
        wireMockServer.stubFor(get(urlMatching("/bibsys.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("")));

        final String clientId = "EssServiceIT#metaproxyInternalServerError";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(),
                    is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }

        assertThat("Usage log", getUsageByClientId(clientId), is(Collections.emptyList()));
    }

    @Test
    void openFormatNotFoundResponse() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withStatus(404)));

        final String clientId = "EssServiceIT#openFormatNotFoundResponse";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("error"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void openFormatConnectionFailed() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withHeader("Connection","Keep-Alive")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withFault(Fault.EMPTY_RESPONSE)));


        final String clientId = "EssServiceIT#openFormatConnectionFailed";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("error"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void openFormatFormatErrorResponse(){
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_error_response.xml")));

        final String clientId = "EssServiceIT#openFormatFormatErrorResponse";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("error"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void openFormatTrackingIdPassed() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .withRequestBody(matchingJsonPath("$.trackingId", containing("track1234")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#openFormatTrackingIdPassed";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .withQueryParameter("trackingId", "track1234")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("netpunkt_standard"));
        }
    }

    @Test
    void externalDatabaseGarbledEscapingResponse() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_garbled_escaping_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#externalDatabaseGarbledEscapingResponse";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("error"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void externalDatabaseDuplicateRecordsResponse() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_duplicate_record_response.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#externalDatabaseDuplicateRecordsResponse";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(), is(Response.Status.OK.getStatusCode()));

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of hits", essResponse.hits, is(5800L));
            assertThat("number of records", essResponse.records.size(), is(1));
            final Element recordElement = (Element)essResponse.records.get(0);
            // Testing returned XML document for correct structure
            assertThat(recordElement.getTagName(), is("error"));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(1))));
    }

    @Test
    void externalDatabaseRespondsWithNotFound() {
        wireMockServer.givenThat(get(urlMatching(".*query=horse.*"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("")));

        final String clientId = "EssServiceIT#externalDatabaseRespondsWithNotFound";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(),
                    is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }

        assertThat("Usage log", getUsageByClientId(clientId), is(Collections.emptyList()));
    }

    @Test
    void externalDatabaseTimeout() {
        // Testing Read-timeout
        wireMockServer.stubFor(get(urlMatching("/.*?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));

        final String clientId = "EssServiceIT#externalDatabaseTimeout";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "1")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            assertThat("service response", response.getStatus(),
                    is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }

        assertThat("Usage log", getUsageByClientId(clientId), is(Collections.emptyList()));
    }

    @Test
    void maxPageSize() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=5")) // todo: had to alter this!
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("response_max_page_5.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#maxPageSize";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("rows", "5")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of records", essResponse.records.size(), is(5));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(5))));
    }

    @Test
    void maxPageSizeDefaultToMax() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("response_max_page_5.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        final String clientId = "EssServiceIT#maxPageSizeDefaultToMax";

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .withQueryParameter("clientId", clientId)
                .withQueryParameter("agencyId", "123456")
                .execute()) {

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of records", essResponse.records.size(), is(5));
        }

        assertThat("Usage log", getUsageByClientId(clientId),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withClientId(clientId)
                                .withAgencyId("123456")
                                .withRecordCount(5))));
    }

    @Test
    void nullClientIdStillUsageLogged() {
        // Stubbing request to base
        wireMockServer.stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=25"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("response_max_page_5.xml")));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/format"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","application/json;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.json")));

        try (Response response = new HttpGet(httpClient)
                .withBaseUrl(serviceBaseUrl)
                .withPathElements("api")
                .withQueryParameter("base", "bibsys")
                .withQueryParameter("query", "horse")
                .withQueryParameter("format", "netpunkt_standard")
                .execute()) {

            final EssResponse essResponse = response.readEntity(EssResponse.class);
            assertThat("number of records", essResponse.records.size(), is(5));
        }

        assertThat("Usage log", getUsageByClientId(null),
                is(Collections.singletonList(
                        new Usage()
                                .withDatabaseId("bibsys")
                                .withRecordCount(5))));
                                
    }

}
