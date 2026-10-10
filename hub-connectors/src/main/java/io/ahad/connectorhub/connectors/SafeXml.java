package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
/** Namespace-aware XML parsing, with DTDs, XInclude and all external entities disabled. */
public final class SafeXml {
    private SafeXml() {
    }
    public static Document parse(String xml) {
        if(xml==null||xml.length()>2_097_152)throw new HubException("XML_INVALID",400);
        try {
            var f=DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(SAXParseException e)throws SAXException {
                    throw e;
                }
            }
            );
            builder.setEntityResolver((publicId,systemId)-> {
                throw new SAXException("External entities forbidden");
            }
            );
            return builder.parse(new InputSource(new StringReader(xml)));
        }
        catch(Exception e) {
            throw new HubException("XML_INVALID",400);
        }
    }
    public static Object json(String xml) {
        var root=parse(xml).getDocumentElement();
        return Map.of(name(root),element(root,0));
    }
    private static String name(Node n) {
        return n.getLocalName()==null?n.getNodeName():n.getLocalName();
    }
    private static Object element(Element e,int depth) {
        if(depth>64)throw new HubException("XML_INVALID",400);
        var m=new LinkedHashMap<String,Object>();
        var attributes=e.getAttributes();
        for(int i=0; i<attributes.getLength(); i++) {
            Node a=attributes.item(i);
            if(!a.getNodeName().startsWith("xmlns"))m.put("@"+name(a),a.getNodeValue());
        }
        StringBuilder text=new StringBuilder();
        var children=e.getChildNodes();
        for(int i=0; i<children.getLength(); i++) {
            Node n=children.item(i);
            if(n instanceof Element child) {
                String key=name(child);
                Object value=element(child,depth+1);
                if(m.containsKey(key)) {
                    Object old=m.get(key);
                    if(old instanceof Repeated r)r.add(value);
                    else {
                        var r=new Repeated();
                        r.add(old);
                        r.add(value);
                        m.put(key,r);
                    }
                }
                else m.put(key,value);
            }
            else if(n.getNodeType()==Node.TEXT_NODE||n.getNodeType()==Node.CDATA_SECTION_NODE)text.append(n.getNodeValue());
        }
        if(m.isEmpty())return text.toString().trim();
        if(!text.toString().isBlank())m.put("#text",text.toString().trim());
        return m;
    }
    private static class Repeated extends ArrayList<Object> {
    }
    public static boolean hasSoapFault(Document doc) {
        return doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/","Fault").getLength()>0||doc.getElementsByTagNameNS("http://www.w3.org/2003/05/soap-envelope","Fault").getLength()>0;
    }
    public static Map<String,Object> wsdl(String xml) {
        Document doc=parse(xml);
        String ns="http://schemas.xmlsoap.org/wsdl/";
        var operations=new ArrayList<Object>();
        var ports=doc.getElementsByTagNameNS(ns,"portType");
        for(int i=0; i<ports.getLength(); i++) {
            Element p=(Element)ports.item(i);
            var ops=p.getElementsByTagNameNS(ns,"operation");
            for(int j=0; j<ops.getLength(); j++) {
                Element op=(Element)ops.item(j);
                operations.add(Map.of("portType",p.getAttribute("name"),"name",op.getAttribute("name")));
            }
        }
        var endpoints=new ArrayList<Object>();
        var actions=new ArrayList<Object>();
        for(String soapNs:List.of("http://schemas.xmlsoap.org/wsdl/soap/","http://schemas.xmlsoap.org/wsdl/soap12/")) {
            var nodes=doc.getElementsByTagNameNS(soapNs,"address");
            for(int i=0; i<nodes.getLength(); i++)endpoints.add(((Element)nodes.item(i)).getAttribute("location"));
            var soapOps=doc.getElementsByTagNameNS(soapNs,"operation");
            for(int i=0; i<soapOps.getLength(); i++) {
                Element op=(Element)soapOps.item(i);
                actions.add(Map.of("name",((Element)op.getParentNode()).getAttribute("name"),"soapAction",op.getAttribute("soapAction")));
            }
        }
        return Map.of("operations",operations,"endpoints",endpoints,"actions",actions,"remoteImportsFollowed",false);
    }
}
