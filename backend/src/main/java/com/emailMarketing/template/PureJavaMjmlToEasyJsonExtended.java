package com.emailMarketing.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.*;

/**
 * Extended Pure-Java MJML -> Easy-Email-ish JSON converter.
 * - Tries to map many MJML tags to editor JSON structure
 * - Produces "type", "id", "attributes", "data.value", "children" fields
 *
 * NOTE: This is a best-effort mapping and does not fully reproduce
 * the full easy-email-core format (which includes block definitions, style arrays, etc).
 */
public class PureJavaMjmlToEasyJsonExtended {

    private static final ObjectMapper M = new ObjectMapper();

    private static final Map<String, String> TAG_MAP = new HashMap<>();
    private static final Set<String> STRUCTURAL = new HashSet<>(Arrays.asList("page","section","wrapper","group","column"));
    static {
        // Core layout
        TAG_MAP.put("mjml", "page");
        TAG_MAP.put("mj-head", "head");
        TAG_MAP.put("mj-body", "body");
        TAG_MAP.put("mj-wrapper", "wrapper");
        TAG_MAP.put("mj-section", "section");
        TAG_MAP.put("mj-group", "group");
        TAG_MAP.put("mj-column", "column");

        // Content blocks
        TAG_MAP.put("mj-text", "text");
        TAG_MAP.put("mj-button", "button");
        TAG_MAP.put("mj-image", "image");
        TAG_MAP.put("mj-divider", "divider");
        TAG_MAP.put("mj-spacer", "spacer");
        TAG_MAP.put("mj-raw", "raw");
        TAG_MAP.put("mj-hero", "hero");
        TAG_MAP.put("mj-navbar", "navbar");
        TAG_MAP.put("mj-social", "social");
        TAG_MAP.put("mj-accordion", "accordion");
        TAG_MAP.put("mj-carousel", "carousel");
        TAG_MAP.put("mj-attributes", "attributes");
        TAG_MAP.put("mj-style", "style");
        TAG_MAP.put("mj-preview", "preview");
        TAG_MAP.put("mj-font", "font");
        TAG_MAP.put("mj-class", "class");
        // social sub-elements
        TAG_MAP.put("mj-social-element", "social-element");
        // accordion sub
        TAG_MAP.put("mj-accordion-element", "accordion-element");
        TAG_MAP.put("mj-accordion-title", "accordion-title");
        TAG_MAP.put("mj-accordion-text", "accordion-text");
        // carousel image
        TAG_MAP.put("mj-carousel-image", "carousel-image");
        // navbar link
        TAG_MAP.put("mj-navbar-link", "navbar-link");
    }

    public static ObjectNode convert(String mjml) {
        String cleaned = sanitizeMjml(mjml);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc;
        try {
            doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(cleaned)));
        } catch (Exception ex) {
            // Graceful fallback: return minimal page JSON rather than propagating SAXParseException
            System.err.println("[MJML-CONVERT] Failed to parse MJML: " + ex.getMessage());
            return minimalFallbackPage();
        }
        Element root = doc.getDocumentElement();

        // create top-level page object
        ObjectNode page = M.createObjectNode();
        page.put("type", "page");
        page.put("id", generateId());

        // collect head data (attributes, styles, preview)
        HeadData headData = collectHead(root);
        if (headData.preview != null) page.put("preview", headData.preview);
        if (headData.headStyle.length() > 0) page.put("headStyle", headData.headStyle.toString());

        // map root attributes (like width/background-color) from root or mj-body
        ObjectNode pageAttrs = M.createObjectNode();
        if (root.hasAttributes()) {
            NamedNodeMap rootAttrs = root.getAttributes();
            for (int i = 0; i < rootAttrs.getLength(); i++) {
                Attr a = (Attr) rootAttrs.item(i);
                pageAttrs.put(a.getName(), a.getValue());
            }
        }
        // Ensure width default so downstream editor never sees undefined width
        if (!pageAttrs.has("width")) {
            pageAttrs.put("width", "600px");
        }

        // find mj-body element to use as main children container
        Element bodyEl = findFirstChildElement(root, "mj-body");
        if (bodyEl == null) {
            // sometimes root itself contains sections
            bodyEl = root;
        }

        // create data.value default (some editor defaults)
        ObjectNode dataNode = M.createObjectNode();
        ObjectNode valueNode = M.createObjectNode();
        valueNode.put("breakpoint", headData.attributes.getOrDefault("breakpoint", "480px"));
        valueNode.put("font-family", headData.attributes.getOrDefault("font-family", "Arial, Helvetica, sans-serif"));
        valueNode.put("text-color", headData.attributes.getOrDefault("text-color", "#000000"));
        dataNode.set("value", valueNode);
        page.set("data", dataNode);

        // set attributes if present
        if (pageAttrs.size() > 0) page.set("attributes", pageAttrs);

        // convert children
        ArrayNode children = M.createArrayNode();
        NodeList nl = bodyEl.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                children.add(elementToEasyJson((Element) n));
            }
        }
        page.set("children", children);

        // Guarantee structural defaults: attributes + data.value {}
        if (!page.has("attributes")) {
            ObjectNode attrsNode = M.createObjectNode();
            attrsNode.put("width", "600px");
            page.set("attributes", attrsNode);
        } else {
            ObjectNode attrsNode = (ObjectNode) page.get("attributes");
            if (!attrsNode.has("width")) attrsNode.put("width", "600px");
        }
        if (!page.has("data")) {
            ObjectNode dn = M.createObjectNode();
            ObjectNode vn = M.createObjectNode();
            dn.set("value", vn);
            page.set("data", dn);
        }
        if (!page.has("children")) {
            page.set("children", M.createArrayNode());
        }

        return page;
    }

    /** Remove BOM / stray leading characters and isolate the <mjml> root if extra data precedes it. */
    private static String sanitizeMjml(String raw) {
        if (raw == null) return "<mjml><mj-body></mj-body></mjml>";
        String s = raw.replace('\uFEFF', ' ').trim();
        // If there's accidental JSON or prose before the first <mjml, slice from there
        int idx = s.toLowerCase().indexOf("<mjml");
        if (idx > 0) {
            s = s.substring(idx);
        }
        // If still no <mjml, wrap contents inside a skeleton to avoid empty parse
        if (!s.toLowerCase().contains("<mjml")) {
            s = "<mjml><mj-body>" + escapeXmlFragment(s) + "</mj-body></mjml>";
        }
        return s;
    }

    private static String escapeXmlFragment(String frag) {
        if (frag == null) return "";
        return frag.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Build a minimal, structurally safe page node for fallback scenarios. */
    public static ObjectNode minimalFallbackPage() {
        ObjectNode page = M.createObjectNode();
        page.put("type", "page");
        page.put("id", generateId());
        ObjectNode attrs = M.createObjectNode();
        attrs.put("width", "600px");
        page.set("attributes", attrs);
        ObjectNode data = M.createObjectNode();
        ObjectNode val = M.createObjectNode();
        val.put("breakpoint", "480px");
        val.put("font-family", "Arial, Helvetica, sans-serif");
        data.set("value", val);
        page.set("data", data);
        page.set("children", M.createArrayNode());
        return page;
    }

    // Basic holder for head parsing
    private static class HeadData {
        Map<String, String> attributes = new HashMap<>();
        String preview = null;
        StringBuilder headStyle = new StringBuilder();
    }

    // collect mj-head nodes (mj-attributes, mj-style, mj-preview, mj-font, etc)
    private static HeadData collectHead(Element root) {
        HeadData h = new HeadData();
        Element head = findFirstChildElement(root, "mj-head");
        if (head == null) return h;

        NodeList nl = head.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String tag = el.getTagName();

            switch (tag) {
                case "mj-attributes":
                    // collect default attributes by selector name or tag
                    NodeList attrChildren = el.getChildNodes();
                    for (int j = 0; j < attrChildren.getLength(); j++) {
                        Node ac = attrChildren.item(j);
                        if (ac.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element ael = (Element) ac;
                        NamedNodeMap attrs = ael.getAttributes();
                        for (int k = 0; k < attrs.getLength(); k++) {
                            Attr at = (Attr) attrs.item(k);
                            // store last attribute value using key tagName:attrName (approx)
                            h.attributes.put(ael.getTagName() + ":" + at.getName(), at.getValue());
                        }
                    }
                    break;
                case "mj-style":
                    h.headStyle.append(getElementText(el)).append("\n");
                    break;
                case "mj-preview":
                    h.preview = getElementText(el).trim();
                    break;
                case "mj-font":
                    // e.g. name + href
                    if (el.hasAttribute("name") && el.hasAttribute("href")) {
                        h.headStyle.append("/* font ").append(el.getAttribute("name")).append(" => ").append(el.getAttribute("href")).append(" */\n");
                    }
                    break;
                case "mj-breakpoint":
                    if (el.hasAttribute("width")) h.attributes.put("breakpoint", el.getAttribute("width"));
                    break;
                case "mj-class":
                    // skip for now: classes would require mapping to style objects
                    break;
                default:
                    // unknown head tag -> append text
                    h.headStyle.append("/* ").append(tag).append(" */\n");
                    break;
            }
        }
        return h;
    }

    private static Element findFirstChildElement(Element parent, String tagName) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && ((Element) n).getTagName().equals(tagName)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static ObjectNode elementToEasyJson(Element el) {
        String tag = el.getTagName();
        String mappedType = TAG_MAP.getOrDefault(tag, tag.replace("mj-", ""));
        ObjectNode node = M.createObjectNode();
        node.put("type", mappedType);
        node.put("id", generateId());

        // attributes -> attributes map
        NamedNodeMap attrs = el.getAttributes();
        if (attrs != null && attrs.getLength() > 0) {
            ObjectNode attrNode = M.createObjectNode();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr a = (Attr) attrs.item(i);
                attrNode.put(a.getName(), a.getValue());
            }
            node.set("attributes", attrNode);
        }

        // data.value object for editor-friendly properties
        ObjectNode dataNode = M.createObjectNode();
        ObjectNode valueNode = M.createObjectNode();

        // children
        ArrayNode children = M.createArrayNode();
        StringBuilder textCollector = new StringBuilder();

        NodeList nl = el.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element ce = (Element) child;
                String cTag = ce.getTagName();

                // special handling for known composite child elements
                if ("mj-social-element".equals(cTag)) {
                    ObjectNode socialEl = M.createObjectNode();
                    socialEl.put("type", "social-element");
                    socialEl.put("id", generateId());
                    ObjectNode socialData = M.createObjectNode();
                    ObjectNode socialValue = M.createObjectNode();
                    if (ce.hasAttribute("href")) socialValue.put("href", ce.getAttribute("href"));
                    if (ce.hasAttribute("name")) socialValue.put("name", ce.getAttribute("name"));
                    if (ce.hasAttribute("src")) socialValue.put("src", ce.getAttribute("src"));
                    String text = getElementText(ce);
                    if (!text.isEmpty()) socialValue.put("content", text);
                    socialData.set("value", socialValue);
                    socialEl.set("data", socialData);
                    children.add(socialEl);
                    continue;
                }

                if ("mj-carousel-image".equals(cTag)) {
                    ObjectNode img = M.createObjectNode();
                    img.put("type", "carousel-image");
                    img.put("id", generateId());
                    ObjectNode d = M.createObjectNode();
                    ObjectNode v = M.createObjectNode();
                    if (ce.hasAttribute("src")) v.put("src", ce.getAttribute("src"));
                    if (ce.hasAttribute("alt")) v.put("alt", ce.getAttribute("alt"));
                    if (ce.hasAttribute("href")) v.put("href", ce.getAttribute("href"));
                    d.set("value", v);
                    img.set("data", d);
                    children.add(img);
                    continue;
                }

                if ("mj-accordion-element".equals(cTag)) {
                    ObjectNode acel = M.createObjectNode();
                    acel.put("type", "accordion-element");
                    acel.put("id", generateId());
                    ArrayNode acelChildren = M.createArrayNode();
                    NodeList inner = ce.getChildNodes();
                    for (int k = 0; k < inner.getLength(); k++) {
                        Node in = inner.item(k);
                        if (in.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element ie = (Element) in;
                        ObjectNode childNode = elementToEasyJson(ie);
                        acelChildren.add(childNode);
                    }
                    acel.set("children", acelChildren);
                    children.add(acel);
                    continue;
                }

                // default recursion for general elements
                children.add(elementToEasyJson(ce));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String t = child.getTextContent().trim();
                if (!t.isEmpty()) {
                    textCollector.append(t);
                    // preserve spacing between text nodes
                    textCollector.append(" ");
                }
            }
        }

        // Map special attributes into data.value for common types
        if ("text".equals(mappedType)) {
            String text = textCollector.toString().trim();
            if (!text.isEmpty()) valueNode.put("content", text);
        } else if ("button".equals(mappedType)) {
            if (el.hasAttribute("href")) valueNode.put("href", el.getAttribute("href"));
            String text = textCollector.toString().trim();
            if (!text.isEmpty()) valueNode.put("content", text);
        } else if ("image".equals(mappedType)) {
            if (el.hasAttribute("src")) valueNode.put("src", el.getAttribute("src"));
            if (el.hasAttribute("alt")) valueNode.put("alt", el.getAttribute("alt"));
        } else if ("hero".equals(mappedType)) {
            if (el.hasAttribute("background-url")) valueNode.put("background-url", el.getAttribute("background-url"));
            if (el.hasAttribute("background-color")) valueNode.put("background-color", el.getAttribute("background-color"));
            String text = textCollector.toString().trim();
            if (!text.isEmpty()) valueNode.put("content", text);
        } else if ("divider".equals(mappedType)) {
            // map border attributes
            if (el.hasAttribute("border-width")) valueNode.put("border-width", el.getAttribute("border-width"));
            if (el.hasAttribute("border-color")) valueNode.put("border-color", el.getAttribute("border-color"));
            if (el.hasAttribute("padding")) valueNode.put("padding", el.getAttribute("padding"));
        } else if ("spacer".equals(mappedType)) {
            if (el.hasAttribute("height")) valueNode.put("height", el.getAttribute("height"));
        } else if ("navbar".equals(mappedType)) {
            // collect links in children array already handled
        } else if ("social".equals(mappedType)) {
            // social children handled earlier
        } else if ("accordion".equals(mappedType)) {
            // children are accordion-elements created earlier
        } else if ("carousel".equals(mappedType)) {
            // children are carousel-image nodes
        }

        // add any inline style-like attributes into data.value
        if (attrs != null && attrs.getLength() > 0) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr a = (Attr) attrs.item(i);
                String aname = a.getName();
                String aval = a.getValue();
                // common style-like attributes
                if ("font-size".equals(aname) || "font-family".equals(aname) || "color".equals(aname) ||
                        "background-color".equals(aname) || "width".equals(aname) || "padding".equals(aname) ||
                        "align".equals(aname) || "line-height".equals(aname)) {
                    valueNode.put(aname, aval);
                }
            }
        }

        if (valueNode.size() > 0) {
            dataNode.set("value", valueNode);
            node.set("data", dataNode);
        }

        if (children.size() > 0) node.set("children", children);
    else if (!node.has("children")) node.set("children", M.createArrayNode());

        // if node has textual content but is not a special content type, attach as data.value.content
        if (children.size() == 0 && textCollector.length() > 0 && !node.has("data")) {
            ObjectNode dd = M.createObjectNode();
            ObjectNode vv = M.createObjectNode();
            vv.put("content", textCollector.toString().trim());
            dd.set("value", vv);
            node.set("data", dd);
        }

        // Structural defaults to align with frontend normalization (prevent width/data undefined)
        if (STRUCTURAL.contains(mappedType)) {
            if (!node.has("attributes")) node.set("attributes", M.createObjectNode());
            ObjectNode attrsNode = (ObjectNode) node.get("attributes");
            if (!attrsNode.has("width")) attrsNode.put("width", "600px");
        }
        if (!node.has("data")) {
            ObjectNode dn = M.createObjectNode();
            ObjectNode vn = M.createObjectNode();
            dn.set("value", vn);
            node.set("data", dn);
        }

        return node;
    }

    private static String getElementText(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList nl = el.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(n.getTextContent());
            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(getElementText((Element) n));
            }
        }
        return sb.toString().trim();
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // quick demo main
    public static void main(String[] args) throws Exception {
        String mjml = "<mjml>\n" +
                "  <mj-head>\n" +
                "    <mj-preview>Preview text</mj-preview>\n" +
                "    <mj-breakpoint width=\"600px\" />\n" +
                "    <mj-style>h1 { color: red; }</mj-style>\n" +
                "  </mj-head>\n" +
                "  <mj-body background-color=\"#efefef\">\n" +
                "    <mj-section>\n" +
                "      <mj-column>\n" +
                "        <mj-text font-size=\"18px\">Hello from MJML</mj-text>\n" +
                "        <mj-button href=\"https://mjml.io\">Click me</mj-button>\n" +
                "        <mj-social>\n" +
                "          <mj-social-element name=\"facebook\" href=\"https://facebook.example\">FB</mj-social-element>\n" +
                "          <mj-social-element name=\"twitter\" href=\"https://twitter.example\">TW</mj-social-element>\n" +
                "        </mj-social>\n" +
                "      </mj-column>\n" +
                "    </mj-section>\n" +
                "  </mj-body>\n" +
                "</mjml>";
        ObjectNode json = convert(mjml);
        System.out.println(M.writerWithDefaultPrettyPrinter().writeValueAsString(json));
    }
}
