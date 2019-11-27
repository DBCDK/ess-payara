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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * This class defines the other classes that make up this JAX-RS application by
 * having the getClasses method return a specific set of resources.
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@ApplicationPath("/api")
public class EssApplication extends Application {

    private static final Set<Class<?>> classes = new HashSet<>();
    static {
        classes.add(ExternalSearchService.class);
        classes.add(HowRU.class);
    }

    @Override
    public Set<Class<?>> getClasses() { return classes; }

    @Override
    public Map<String, Object> getProperties() {
        return new HashMap<String, Object>() {{
            put("jersey.config.server.disableMoxyJson", true);
        }};
    }
}
