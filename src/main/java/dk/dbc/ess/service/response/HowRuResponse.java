package dk.dbc.ess.service.response;

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

    public HowRuResponse(String error) {
        this.ok = error == null || error.isEmpty();
        this.message = this.ok ? null : error;
    }
}
