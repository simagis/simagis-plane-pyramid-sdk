/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Daniel Alievsky, AlgART Laboratory (http://algart.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.algart.simagis.imageio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class IIOMetadataToJsonConverter {
    private static final String XMP_CODE_LINE_IN_JPEG = "http://ns.adobe.com/xap/1.0/\u0000";
    private static final int MAX_NON_COMPACTED_SIMILAR_CHILDREN = 4;
    private static final String[] COMPACTED_SIMILAR_CHILDREN_NAMES = {"TIFFByte", "TIFFShort", "TIFFLong"};

    // Maybe it will have some customized parameters in future

    public IIOMetadataToJsonConverter() {
    }

    public final JSONObject toJson(IIOMetadata metadata) throws JSONException {
        if (metadata == null)
            throw new NullPointerException("Null metadata");
        JSONObject result = new JSONObject();
        JSONArray formats = new JSONArray();
        final String[] formatNames = metadata.getMetadataFormatNames();
        if (formatNames != null) {
            for (String formatName : formatNames) {
                final Node tree = metadata.getAsTree(formatName);
                JSONObject format = new JSONObject();
                format.put("formatName", formatName);
                format.put("tree", treeToJson(tree));
                formats.put(format);
            }
        }
        result.put("formats", formats);
        return result;
    }

    private JSONObject treeToJson(Node node) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("name", node.getNodeName());
        result.put("value", node.getNodeValue());
        final NamedNodeMap nodeAttributes = node.getAttributes();
        if (nodeAttributes != null) {
            final int length = nodeAttributes.getLength();
            if (length > 0) {
                JSONObject attributes = new JSONObject();
                for (int k = 0; k < length; k++) {
                    final Node nodeAttr = nodeAttributes.item(k);
                    attributes.put(nodeAttr.getNodeName(), nodeAttr.getNodeValue());
                }
                result.put("attributes", attributes);
            }
        }
        JSONObject compactChildren = null;
        for (String childrenName : COMPACTED_SIMILAR_CHILDREN_NAMES) {
            compactChildren = compactTIFFSimilarNodes(node, childrenName);
            if (compactChildren != null) {
                break;
            }
        }
        if (compactChildren != null) {
            result.put("joinedNodes", compactChildren);
        } else {
            Node child = node.getFirstChild();
            if (child != null) {
                JSONArray nodes = new JSONArray();
                do {
                    nodes.put(treeToJson(child));
                    child = child.getNextSibling();
                } while (child != null);
                result.put("nodes", nodes);
            }
        }
        if (node instanceof IIOMetadataNode) {
            result.put("userObject", iioNodeUserObjectToJson((IIOMetadataNode) node));
        }
        return result;
    }

    private JSONObject compactTIFFSimilarNodes(Node node, String childrenName) throws JSONException {
        assert node != null;
        assert childrenName != null;
        long count = 0;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling(), count++) {
            if (!childrenName.equals(child.getNodeName())) {
                return null;
            }
            // so, a child has the name, equal to childrenName
            if (child.getFirstChild() != null) {
                return null;
            }
            // so, a child has no own children
            final NamedNodeMap childAttributes = child.getAttributes();
            if (childAttributes == null || childAttributes.getLength() != 1) {
                return null;
            }
            Node childAttr = childAttributes.item(0);
            String attrName = childAttr.getNodeName();
            String attrValue = childAttr.getNodeValue();
            if (!("value".equals(attrName)) || attrValue == null || attrValue.contains(",")) {
                return null;
            }
            // so, a child has only 1 attribute "value", which is a string, not containing ","
        }
        if (count <= MAX_NON_COMPACTED_SIMILAR_CHILDREN) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        count = 0;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling(), count++) {
            Node childAttr = child.getAttributes().item(0);
            if (count > 0) {
                sb.append(",");
            }
            sb.append(childAttr.getNodeValue());
        }
        JSONObject result = new JSONObject();
        result.put("childrenName", childrenName);
        result.put("childrenValue", sb.toString());
        return result;
    }

    private JSONObject iioNodeUserObjectToJson(IIOMetadataNode node) throws JSONException {
        final Object userObject = node.getUserObject();
        if (userObject == null) {
            return null;
        }
        JSONObject result = new JSONObject();
        result.put("class", userObject.getClass().getCanonicalName());
        final String stringRepresentation = userObject.toString();
        final String javaDefaultStringRepresentation = userObject.getClass().getName()
            + "@" + Integer.toHexString(System.identityHashCode(userObject)); // see JavaDoc to Object.toString
        if (!stringRepresentation.equals(javaDefaultStringRepresentation)) {
            // so, we can hope for some non-trivial information in toString()
            result.put("toString", stringRepresentation);
        }
        if (userObject instanceof byte[]) {
            byte[] bytes = (byte[]) userObject;
            result.put("valueLength", bytes.length);
            if (bytes.length <= 16384) {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < bytes.length; k++) {
                    // formatting bytes like it is done in TIFFUndefined value in TIFF metadata
                    if (k > 0) {
                        sb.append(",");
                    }
                    sb.append(bytes[k] & 0xFF);
                }
                result.put("valueBytes", sb.toString());
            }
        }
        JSONObject extendedInfo;
        try {
            extendedInfo = extendedUserObjectToJson(userObject);
        } catch (Exception e) {
            extendedInfo = exceptionToJson(e);
        }
        result.put("extendedInfo", extendedInfo);
        return result;
    }

    private JSONObject extendedUserObjectToJson(Object userObject)
        throws JSONException, IOException
    {
        if (userObject instanceof byte[]) {
            byte[] bytes = (byte[]) userObject;
            if (bytes[0] == 'E'
                && bytes[1] == 'x'
                && bytes[2] == 'i'
                && bytes[3] == 'f'
                && bytes[4] == 0
                && bytes[5] == 0)
            {
                final InputStream inputStream = new ByteArrayInputStream(bytes, 6, bytes.length - 6);
                try {
                    final JSONObject exif = extendedReadExif(inputStream);
                    if (exif == null) {
                        return null;
                    }
                    JSONObject result = new JSONObject();
                    result.put("com.simagis.imageio.metadata.Exif", exif);
//                    System.err.println("Found com.simagis.imageio.metadata.Exif!");
                    return result;
                } finally {
                    inputStream.close();
                }
            }
            final int xmpCodeLineLength = XMP_CODE_LINE_IN_JPEG.length();
            boolean isXMP = bytes.length >= xmpCodeLineLength;
            if (isXMP) {
                for (int k = 0; k < xmpCodeLineLength; k++) {
                    isXMP &= bytes[k] == XMP_CODE_LINE_IN_JPEG.charAt(k);
                }
            }
            if (isXMP) {
                String s = new String(bytes, xmpCodeLineLength, bytes.length - xmpCodeLineLength, "UTF-8");
                JSONObject result = new JSONObject();
                result.put("com.simagis.imageio.metadata.XMP", s);
//                System.err.println("Found com.simagis.imageio.metadata.XMP!");
                return result;
            }
        }
        return null;
    }

    private JSONObject extendedReadExif(InputStream inputStream) throws IOException, JSONException {
        assert inputStream != null;
        ImageInputStream iis = ImageIO.createImageInputStream(inputStream);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        try {
            if (!readers.hasNext()) {
                return null; // no readers to read such "Exif"
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return toJson(reader.getImageMetadata(0));
            } finally {
                reader.dispose();
            }
        } finally {
            iis.close();
        }
    }

    private static JSONObject exceptionToJson(Exception e) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("error", e.toString());
        return result;
    }
}
