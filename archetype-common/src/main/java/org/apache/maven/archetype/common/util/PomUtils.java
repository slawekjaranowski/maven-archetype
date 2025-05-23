/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.archetype.common.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import org.apache.maven.archetype.exception.InvalidPackaging;
import org.apache.maven.archetype.old.ArchetypeTemplateProcessingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * POM helper class.
 */
public final class PomUtils {

    private PomUtils() {
        throw new IllegalStateException("no instantiable constructor");
    }

    /**
     * Adds module {@code artifactId} unless the module already presents in {@code fileReader}.
     *
     * @param artifactId artifactId of module to add
     * @param fileReader source POM XML
     * @param fileWriter target XML
     * @return {@code true} if modules section in POM is empty or does not exist or {@code artifactId} does not appear
     * a module in {@code fileReader} XML.
     * @throws IOException if I/O error
     * @throws InvalidPackaging if packaging is not "pom" or not exist in POM
     * @throws ArchetypeTemplateProcessingException if "project" does not exist or "modules" element is duplicated
     * @throws ParserConfigurationException if parser error
     * @throws SAXException if parser error
     * @throws TransformerException if an error writing to {@code fileWriter}
     */
    public static boolean addNewModule(String artifactId, Reader fileReader, Writer fileWriter)
            throws ArchetypeTemplateProcessingException, InvalidPackaging, IOException, ParserConfigurationException,
                    SAXException, TransformerException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource inputSource = new InputSource();
        inputSource.setCharacterStream(fileReader);
        Document document = db.parse(inputSource);

        Element project = document.getDocumentElement();
        if (!"project".equals(project.getNodeName())) {
            throw new ArchetypeTemplateProcessingException("Unable to find root element 'project'.");
        }

        String packaging = null;
        Node packagingNode = getChildNode(project, "packaging");
        if (packagingNode != null) {
            packaging = packagingNode.getTextContent();
        }
        if (!"pom".equals(packaging)) {
            throw new InvalidPackaging(
                    "Unable to add module to the current project as it is not of packaging type 'pom'");
        }

        Node modules = getChildNode(project, "modules");

        if (!hasArtifactIdInModules(artifactId, modules)) {
            Element module = document.createElement("module");
            module.setTextContent(artifactId);
            if (modules == null) {
                modules = document.createElement("modules");
                project.appendChild(modules);
            }

            // shift the child node by next two spaces after the parent node spaces
            modules.appendChild(document.createTextNode("  "));

            modules.appendChild(module);

            // shift the end tag </modules>
            modules.appendChild(document.createTextNode("\n  "));

            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            tf.setAttribute("indent-number", 2);
            try (InputStream xsl = PomUtils.class.getResourceAsStream("/prettyprint.xsl")) {
                final Transformer tr = tf.newTransformer(new StreamSource(xsl));
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty(OutputKeys.METHOD, "xml");
                tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                document.getDomConfig().setParameter("infoset", Boolean.TRUE);
                document.getDocumentElement().normalize();
                tr.transform(new DOMSource(document), new StreamResult(fileWriter));
            }
            return true;
        } else {
            return false;
        }
    }

    private static Node getChildNode(Element parent, String name) {
        Node namedNode = null;
        NodeList nodes = parent.getChildNodes();
        for (int len = nodes.getLength(), i = 0; i < len; i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                namedNode = node;
                break;
            }
        }
        return namedNode;
    }

    private static boolean hasArtifactIdInModules(String artifactId, Node modules) {
        if (modules != null) {
            Node module = modules.getFirstChild();
            while (module != null) {
                if (module.getNodeType() == Node.ELEMENT_NODE && artifactId.equals(module.getTextContent())) {
                    return true;
                }
                module = module.getNextSibling();
            }
        }
        return false;
    }
}
