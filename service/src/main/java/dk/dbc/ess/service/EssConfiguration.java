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

import javax.annotation.PostConstruct;
import javax.ejb.EJBException;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import static java.util.stream.Collectors.toMap;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@ApplicationScoped
@Startup
public class EssConfiguration  {

    private static final Logger log = LoggerFactory.getLogger(EssConfiguration.class);
    private final Map<String, String> env;
    private String metaProxyUrl;
    private String openFormatUrl;
    private List<String> formats;
    private int maxPageSize;
    private List<String> bases;
    private long jerseyTimeout;
    private long jerseyConnectionTimeout;

    public EssConfiguration() {
        this.env = System.getenv();
    }

    public EssConfiguration(String... params) {
        this.env = Arrays.stream(params)
                .map(s -> s.split("=", 2))
                .collect(toMap(p -> p[0], p -> p[1]));
        loadProperties();
    }

    @PostConstruct
    public void loadProperties() {
        Properties props = findProperties("external-search-service");
        metaProxyUrl = getValue(props, env, "metaProxyUrl", "META_PROXY_URL", null, "No meta proxy URL found");
        openFormatUrl = getValue(props, env, "openFormatUrl", "OPEN_FORMAT_URL", null, "No OpenFormat URL found");
        formats = getValue(props, env, "formats", "FORMATS", "netpunkt_standard", "No formats specified", s -> Arrays.asList(s.split(",")));
        maxPageSize = getValue(props, env, "maxPageSize", "MAX_PAGE_SIZE", "5", "", Integer::parseUnsignedInt);
        bases = getValue(props, env, "bases", "BASES", null, "No bases provided", s -> Arrays.asList(s.split(",")));
        jerseyTimeout = getValue(props, env, "jerseyTimeout", "JERSEY_TIMEOUT", "60", "No jersey timeout specified", Long::parseUnsignedLong);
        jerseyConnectionTimeout = getValue(props, env, "jerseyConnectionTimeout", "JERSEY_CONNECTION_TIMEOUT",
                "300", "No jersey connection timeout specified", Long::parseUnsignedLong);
    }

    public String getMetaProxyUrl() { return metaProxyUrl; }
    public String getOpenFormatUrl() { return openFormatUrl; }
    public List<String> getFormats() { return formats; }
    public int getMaxPageSize() { return maxPageSize; }
    public List<String> getBases() { return bases; }
    public long getJerseyTimeout() { return jerseyTimeout; }
    public long getJerseyConnectionTimeout() { return jerseyConnectionTimeout; }

    private static <T> T getValue(Properties props, Map<String, String> env, String propertyName, String envName, String defaultValue, String error, Function<String, T> mapper) {
        return mapper.apply(getValue(props, env, propertyName, envName, defaultValue, error));
    }

    private static String getValue(Properties props, Map<String, String> env, String propName, String envName, String defaultValue, String error) {
        String val = props.getProperty(propName);
        if (val == null) {
            val = env.get(envName);
        }
        if (val == null) {
            val = defaultValue;
        }
        if (val == null) {
            throw new EJBException(error + ". " +
                    "Please provide env: " + envName + " or property: " + propName);
        }
        return val;
    }

    /**
     * Read properties from a .properties file. Mostly used for internal tests.
     * @param resourceName
     * @return properties
     */
    private Properties findProperties(String resourceName) {
        try {
            InputStream resource = this.getClass().getClassLoader().getResourceAsStream(resourceName + ".properties");
            if (resource != null) {
                Properties propsFromFile = new Properties();
                try {
                    propsFromFile.load(resource);
                    return propsFromFile;
                } catch (IOException e) {
                    throw new EJBException("Property file " + resourceName + ".properties exists, but parsing of it failed.", e);
                }
            }
            Object lookup = InitialContext.doLookup(resourceName);
            if (lookup instanceof Properties) {
                return (Properties) lookup;
            } else {
                throw new NamingException("Found " + resourceName + ", but not of type Properties of type: " + lookup.getClass().getTypeName());
            }
        } catch (NamingException ex) {
            log.error("Exception: {}", ex.getMessage());
        }
        return new Properties();
    }

    protected ClientBuilder getClientBuilder() { return ClientBuilder.newBuilder(); }

}
