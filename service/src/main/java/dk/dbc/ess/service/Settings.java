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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class Settings {

    @NotNull
    private String metaProxyUrl;

    public String getMetaProxyUrl() {
        return metaProxyUrl;
    }

    public void setMetaProxyUrl(String metaProxyUrl) {
        this.metaProxyUrl = metaProxyUrl;
    }

    @NotNull
    private String openFormatUrl;

    public String getOpenFormatUrl() {
        return openFormatUrl;
    }

    public void setOpenFormatUrl(String openFormatUrl) {
        this.openFormatUrl = openFormatUrl;
    }

    @NotNull
    private String bases;

    public Set<String> getBases() {
        return Arrays.stream(bases.split("[ ,]+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public void setBases(String bases) {
        this.bases = bases;
    }

    @NotNull
    private Integer maxPageSize;

    public Integer getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(Integer maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    @Override
    public String toString() {
        return "Settings{" + "metaProxyUrl=" + metaProxyUrl + ", openFormatUrl=" + openFormatUrl + ", bases=" + bases;
    }

}
