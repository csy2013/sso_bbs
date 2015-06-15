package org.ofbiz.sso.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by changshengyong on 15/6/14.
 */
public class UtilXml {

    /** Creates a child element with the given name and appends it to the element child node list.
     *  Also creates a Text node with the given value and appends it to the new elements child node list.
     */
    public static Element addChildElementValue(Element element, String childElementName,
                                               String childElementValue, Document document) {
        Element newElement = addChildElement(element, childElementName, document);

        newElement.appendChild(document.createTextNode(childElementValue));
        return newElement;
    }


    /** Creates a child element with the given name and appends it to the element child node list. */
    public static Element addChildElement(Element element, String childElementName, Document document) {
        Element newElement = document.createElement(childElementName);

        element.appendChild(newElement);
        return newElement;
    }

    /** Return the text (node value) contained by the named child node. */
    public static String childElementValue(Element element, String childElementName) {
        if (element == null) return null;
        // get the value of the first element with the given name
        Element childElement = firstChildElement(element, childElementName);

        return elementValue(childElement);
    }

    /** Return the text (node value) contained by the named child node or a default value if null. */
    public static String childElementValue(Element element, String childElementName, String defaultValue) {
        if (element == null) return defaultValue;
        // get the value of the first element with the given name
        Element childElement = firstChildElement(element, childElementName);
        String elementValue = elementValue(childElement);

        if (UtilValidate.isEmpty(elementValue))
            return defaultValue;
        else
            return elementValue;
    }

    /** Return the first child Element with the given name; if name is null
     * returns the first element. */
    public static Element firstChildElement(Element element, String childElementName) {
        if (element == null) return null;
        if (UtilValidate.isEmpty(childElementName)) return null;
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE && (childElementName == null ||
                        childElementName.equals(node.getLocalName() != null ? node.getLocalName() : node.getNodeName()))) {
                    Element childElement = (Element) node;
                    return childElement;
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }

    /** Return the first child Element with the given name; if name is null
     * returns the first element. */
    public static Element firstChildElement(Element element, String childElementName, String attrName, String attrValue) {
        if (element == null) return null;
        // get the first element with the given name
        Node node = element.getFirstChild();

        if (node != null) {
            do {
                if (node.getNodeType() == Node.ELEMENT_NODE && (childElementName == null ||
                        childElementName.equals(node.getLocalName() != null ? node.getLocalName() : node.getNodeName()))) {
                    Element childElement = (Element) node;

                    String value = childElement.getAttribute(attrName);

                    if (value != null && value.equals(attrValue)) {
                        return childElement;
                    }
                }
            } while ((node = node.getNextSibling()) != null);
        }
        return null;
    }


    /** Return the text (node value) of the first node under this, works best if normalized. */
    public static String elementValue(Element element) {
        if (element == null) return null;
        // make sure we get all the text there...
        element.normalize();
        Node textNode = element.getFirstChild();

        if (textNode == null) return null;

        StringBuilder valueBuffer = new StringBuilder();
        do {
            if (textNode.getNodeType() == Node.CDATA_SECTION_NODE || textNode.getNodeType() == Node.TEXT_NODE) {
                valueBuffer.append(textNode.getNodeValue());
            }
        } while ((textNode = textNode.getNextSibling()) != null);
        return valueBuffer.toString();
    }

    public static Document readXmlDocument(String content)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(content, true);
    }

    public static Document readXmlDocument(String content, boolean validate)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (content == null) {

            return null;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes("UTF-8"));
        return readXmlDocument(bis, validate, "Internal Content");
    }
    public static Document readXmlDocument(InputStream is, String docDescription)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        return readXmlDocument(is, true, docDescription);
    }
    public static Document readXmlDocument(InputStream is, boolean validate, String docDescription)
            throws SAXException, ParserConfigurationException, java.io.IOException {
        if (is == null) {

            return null;
        }


        Document document = null;

        /* Standard JAXP (mostly), but doesn't seem to be doing XML Schema validation, so making sure that is on... */
        DocumentBuilderFactory factory = new org.apache.xerces.jaxp.DocumentBuilderFactoryImpl();
        factory.setValidating(validate);
        factory.setNamespaceAware(true);

        factory.setAttribute("http://xml.org/sax/features/validation", validate);
        factory.setAttribute("http://apache.org/xml/features/validation/schema", validate);

        // with a SchemaUrl, a URL object
        //factory.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", "http://www.w3.org/2001/XMLSchema");
        //factory.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource", SchemaUrl);
        DocumentBuilder builder = factory.newDocumentBuilder();
        if (validate) {
            LocalResolver lr = new LocalResolver(new DefaultHandler());
            ErrorHandler eh = new LocalErrorHandler(docDescription, lr);

            builder.setEntityResolver(lr);
            builder.setErrorHandler(eh);
        }
        document = builder.parse(is);

        return document;
    }


    /**
     * Local entity resolver to handle J2EE DTDs. With this a http connection
     * to sun is not needed during deployment.
     * Function boolean hadDTD() is here to avoid validation errors in
     * descriptors that do not have a DOCTYPE declaration.
     */
    public static class LocalResolver implements EntityResolver {

        private boolean hasDTD = false;
        private EntityResolver defaultResolver;

        public LocalResolver(EntityResolver defaultResolver) {
            this.defaultResolver = defaultResolver;
        }

        /**
         * Returns DTD inputSource. If DTD was found in the dtds Map and inputSource was created
         * flag hasDTD is set to true.
         * @param publicId - Public ID of DTD
         * @param systemId - System ID of DTD
         * @return InputSource of DTD
         */
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            //Debug.logInfo("resolving XML entity with publicId [" + publicId + "], systemId [" + systemId + "]", module);
            hasDTD = false;
                  // nothing found by the public ID, try looking at the systemId, or at least the filename part of it and look for that on the classpath
                int lastSlash = systemId.lastIndexOf("/");
                String filename = null;
                if (lastSlash == -1) {
                    filename = systemId;
                } else {
                    filename = systemId.substring(lastSlash + 1);
                }

                URL resourceUrl = UtilURL.fromResource(filename);

                if (resourceUrl != null) {
                    InputStream resStream = resourceUrl.openStream();
                    InputSource inputSource = new InputSource(resStream);

                    if (UtilValidate.isNotEmpty(publicId)) {
                        inputSource.setPublicId(publicId);
                    }
                    hasDTD = true;
                     return inputSource;
                } else {
                     return null;
                }


        }

        /**
         * Returns the boolean value to inform id DTD was found in the XML file or not
         * @return boolean - true if DTD was found in XML
         */
        public boolean hasDTD() {
            return hasDTD;
        }
    }


    /** Local error handler for entity resolver to DocumentBuilder parser.
     * Error is printed to output just if DTD was detected in the XML file.
     */
    public static class LocalErrorHandler implements ErrorHandler {

        private String docDescription;
        private LocalResolver localResolver;

        public LocalErrorHandler(String docDescription, LocalResolver localResolver) {
            this.docDescription = docDescription;
            this.localResolver = localResolver;
        }

        public void error(SAXParseException exception) {
            String exceptionMessage = exception.getMessage();
            Pattern valueFlexExpr = Pattern.compile("value '\\$\\{.*\\}'");
            Matcher matcher = valueFlexExpr.matcher(exceptionMessage.toLowerCase());
           /* if (localResolver.hasDTD() && !matcher.find()) {
                Debug.logError("XmlFileLoader: File "
                                + docDescription
                                + " process error. Line: "
                                + String.valueOf(exception.getLineNumber())
                                + ". Error message: "
                                + exceptionMessage, module
                );
            }*/
        }

        public void fatalError(SAXParseException exception) {
           /* if (localResolver.hasDTD()) {
                Debug.logError("XmlFileLoader: File "
                                + docDescription
                                + " process fatal error. Line: "
                                + String.valueOf(exception.getLineNumber())
                                + ". Error message: "
                                + exception.getMessage(), module
                );
            }*/
        }

        public void warning(SAXParseException exception) {
           /* if (localResolver.hasDTD()) {
                Debug.logError("XmlFileLoader: File "
                                + docDescription
                                + " process warning. Line: "
                                + String.valueOf(exception.getLineNumber())
                                + ". Error message: "
                                + exception.getMessage(), module
                );
            }*/
        }
    }

}
