package dk.dbc.ess.service.response;

import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
@XmlRootElement(name = "response", namespace = EssResponse.NS)
public class EssResponse {

    public static final String NS = "info:ESSv0";

    @XmlElement(required = true, namespace = NS)
    public long hits;

    @XmlElementWrapper(name = "records", required = true, namespace = NS)
    @XmlAnyElement(lax = true)
    public List<Object> records;

    @XmlElement(name = "trackingId", required = true, namespace = NS)
    public String trackingId;
}
