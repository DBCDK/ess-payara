package dk.dbc.ws;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class UnsignedLongAdapter extends XmlAdapter<String, Long> {

    @Override
    public Long unmarshal(String vt) throws Exception {
        return Long.parseUnsignedLong(vt);
    }

    @Override
    public String marshal(Long bt) throws Exception {
        return String.valueOf(bt);
    }

}
