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
package dk.dbc.ws;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class LongAdapter extends XmlAdapter<String, Long> {

    @Override
    public Long unmarshal(String vt) throws Exception {
        return Long.parseLong(vt);
    }

    @Override
    public String marshal(Long bt) throws Exception {
        return String.valueOf(bt);
    }

}
