package dk.dbc.ws;

import javax.xml.bind.annotation.adapters.XmlAdapter;

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
