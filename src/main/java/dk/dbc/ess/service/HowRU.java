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

import dk.dbc.ess.service.response.HowRuResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@Path("howru")
@Stateless
public class HowRU {

    private static final Logger log = LoggerFactory.getLogger(HowRU.class);

    @Inject
    EssConfiguration essConfiguration;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response howru() {
        // check the two services that ESS communicates with.
        String metaProxyUrl = essConfiguration.getMetaProxyUrl();
        String openFormatUrl = essConfiguration.getOpenFormatUrl();
        String openFormatHowruUrl = getOpenFormatHowruUrl(openFormatUrl).toString();
        if (metaProxyUrlOk(metaProxyUrl) && openFormatUrlOk(openFormatHowruUrl)) {
            return Response.ok(new HowRuResponse(null)).build();
        }
        return Response.serverError().build();
    }

    /**
     * Calls the meta-proxy URL and verifies that the response has status 200 (OK)
     * TODO: if metaproxy has some kind of status/howru/diagnostics endpoint, use that instead.
     * @param url
     * @return true iff the response has status 200
     */
    private boolean metaProxyUrlOk(String url) {
        try {
            URI uri = new URI(url);
            Response r = essConfiguration.getClient().target(uri).request(MediaType.APPLICATION_XML_TYPE).get();
            if (r.getStatus() == 200) {
                return true;
            }
        } catch (Exception e) {
            log.error("Exception occurred when calling MetaProxy url {}: {}", url, e);
            return false;
        }
        log.error("Unexpected response from MetaProxy url {}", url);
        return false;
    }

    /**
     * Calls the specified openFormat URL and checks that we get the expected 200 OK status.
     * @param url
     * @return whether the openFormat service responds as expected.
     */
    private boolean openFormatUrlOk(String url) {
        try {
            URI uri = new URI(url);
            Response r = essConfiguration.getClient().target(uri).request().get();
            if (r.getStatus() == 200) {
                return true;
            }
        } catch (Exception e) {
            log.error("Exception occurred in HowRU call to openFormat url: {}: {}", url, e);
            return false;
        }
        log.error("Unexpected response from HowRU call to openFormat url: {}", url);
        return false;
    }

    private URL getOpenFormatHowruUrl(String openFormatUrl) {
        final URL url;
        try {
            url = new URL(openFormatUrl);
            return new URL(url.getProtocol(), url.getHost(), url.getPort() > 0 ? url.getPort() : 80, "/health");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
