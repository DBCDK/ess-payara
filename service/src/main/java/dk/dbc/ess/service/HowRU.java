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

import com.codahale.metrics.health.HealthCheck;
import dk.dbc.ess.service.response.HowRuResponse;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@Path("howru")
public class HowRU {
    private final List<HealthCheck> checks;

    public HowRU(HealthCheck... checks) {
        this.checks = Arrays.asList(checks);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response howru() {
        boolean ok = checks.parallelStream()
                .allMatch(c -> c.execute().isHealthy());
        if (ok) {
            return Response.ok(new HowRuResponse(null)).build();
        } else {
            return Response.status(INTERNAL_SERVER_ERROR).entity(new HowRuResponse("downstream error - check healthchecks on admin url")).build();
        }
    }

}
