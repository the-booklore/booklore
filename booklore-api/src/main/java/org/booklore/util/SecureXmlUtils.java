package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Slf4j
@UtilityClass
public class SecureXmlUtils {

    public static DocumentBuilderFactory createSecureDocumentBuilderFactory(boolean namespaceAware) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(namespaceAware);
            
            // Prevent XXE attacks
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            
            return factory;
        } catch (ParserConfigurationException e) {
            log.warn("Failed to configure secure XML parser, using defaults: {}", e.getMessage());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(namespaceAware);
            return factory;
        }
    }

    public static DocumentBuilder createSecureDocumentBuilder(boolean namespaceAware) 
            throws ParserConfigurationException {
        return createSecureDocumentBuilderFactory(namespaceAware).newDocumentBuilder();
    }
}
