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
package dk.dbc.ess.service.response;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@XmlRootElement(name = "howru")
public class HowRuResponse {

    @XmlElement(name = "ok", required = true, nillable = false)
    public boolean ok;

    @XmlElement(name = "message", required = false, nillable = true)
    public String message;

    public HowRuResponse() {
    }

    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public HowRuResponse(String error) {
        this.ok = error == null || error.isEmpty();
        this.message = this.ok ? null : error;
    }
}
