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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author Noah Torp-Smith (nots@dbc.dk)
 */
public class XmlTools {
    private static final Logger log = LoggerFactory.getLogger(XmlTools.class);
    private static final DocumentBuilderFactory DBF = newDocumentBuilderFactory();

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        synchronized (DocumentBuilderFactory.class) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory;
        }
    }

    public static Transformer newTransformer() {
        try {
            synchronized (TransformerFactory.class) {
                log.info("Creating new transformerfactory");
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                return transformer;
            }
        } catch (TransformerFactoryConfigurationError | TransformerConfigurationException | IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static DocumentBuilder newDocumentBuilder() {
        try {
            synchronized (DBF) {
                log.info("Creating new documentbuilder");
                return DBF.newDocumentBuilder();
            }
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void fixXmlNamespacePrefix(Element element, String metadataPrefix) {
        String namespaceURI = element.getNamespaceURI();
        if (namespaceURI == null) {
            return;
        }
        fixXmlNamespacePrefix(element, metadataPrefix, namespaceURI);
    }

    private static void fixXmlNamespacePrefix(Element element, String metadataPrefix, String namespaceURI) {
        String prefix = null;
        if (namespaceURI.equals(element.getNamespaceURI())) {
            prefix = element.getPrefix();
            if (prefix == null) {
                prefix = "";
            }
            element.setPrefix(metadataPrefix);
        }
        for (Node child = element.getFirstChild() ; child != null ; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                fixXmlNamespacePrefix((Element) child, metadataPrefix, namespaceURI);
            }
        }
        if (prefix != null) {
            element.removeAttribute(prefix.isEmpty() ? "xmlns" :  "xmlns:" + prefix);
        }
    }
}
