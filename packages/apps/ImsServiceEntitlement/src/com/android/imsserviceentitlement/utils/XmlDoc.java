/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.imsserviceentitlement.utils;

import static com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlAttributes.APP_ID;
import static com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlNode.APPLICATION;

import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.imsserviceentitlement.debug.DebugUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Wrap the raw content and parse it into nodes. */
public class XmlDoc {
    private static final String TAG = "IMSSE-XmlDoc";

    private static final String NODE_CHARACTERISTIC = "characteristic";
    private static final String NODE_PARM = "parm";
    private static final String PARM_NAME = "name";
    private static final String PARM_VALUE = "value";

    private final Map<String, Map<String, String>> mNodesMap = new ArrayMap<>();

    public XmlDoc(String responseBody) {
        parseXmlResponse(responseBody);
    }

    /** Returns param value for given node and key. */
    public Optional<String> get(String node, String key, @Nullable String appId) {
        Map<String, String> paramsMap = mNodesMap.get(combineKeyWithAppId(node, appId));
        return Optional.ofNullable(paramsMap == null ? null : paramsMap.get(key));
    }

    private String combineKeyWithAppId(String node, @Nullable String appId) {
        return APPLICATION.equals(node) && !TextUtils.isEmpty(appId) ? node + "_" + appId : node;
    }

    /**
     * Parses the response body as per format defined in TS.43 2.7.2 New Characteristics for
     * XML-Based Document.
     */
    private void parseXmlResponse(String responseBody) {
        if (responseBody == null) {
            return;
        }

        // Workaround: some server doesn't escape "&" in XML response and that will cause XML parser
        // failure later.
        // This is a quick impl of escaping w/o intorducing a ton of new dependencies.
        responseBody = responseBody.replace("&", "&amp;").replace("&amp;amp;", "&amp;");

        try {
            InputSource inputSource = new InputSource(new StringReader(responseBody));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = builderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(inputSource);
            doc.getDocumentElement().normalize();

            if (DebugUtils.isPiiLoggable()) {
                Log.d(
                        TAG,
                        "parseXmlResponseForNode() Root element: "
                                + doc.getDocumentElement().getNodeName());
            }

            NodeList nodeList = doc.getElementsByTagName(NODE_CHARACTERISTIC);
            for (int i = 0; i < nodeList.getLength(); i++) {
                NamedNodeMap map = nodeList.item(i).getAttributes();
                if (DebugUtils.isPiiLoggable()) {
                    Log.d(
                            TAG,
                            "parseAuthenticateResponse() node name="
                                    + nodeList.item(i).getNodeName()
                                    + " node value="
                                    + map.item(0).getNodeValue());
                }
                Map<String, String> paramsMap = new ArrayMap<>();
                Element element = (Element) nodeList.item(i);
                paramsMap.putAll(parseParams(element.getElementsByTagName(NODE_PARM)));
                mNodesMap.put(
                        combineKeyWithAppId(map.item(0).getNodeValue(), paramsMap.get(APP_ID)),
                        paramsMap);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            Log.e(TAG, "Failed to parse XML node. " + e);
        }
    }

    private static Map<String, String> parseParams(NodeList nodeList) {
        Map<String, String> nameValue = new ArrayMap<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            NamedNodeMap map = node.getAttributes();
            String name = "";
            String value = "";
            for (int j = 0; j < map.getLength(); j++) {
                if (PARM_NAME.equals(map.item(j).getNodeName())) {
                    name = map.item(j).getNodeValue();
                } else if (PARM_VALUE.equals(map.item(j).getNodeName())) {
                    value = map.item(j).getNodeValue();
                }
            }
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(value)) {
                continue;
            }
            nameValue.put(name, value);

            if (DebugUtils.isPiiLoggable()) {
                Log.d(TAG, "parseParams() put name '" + name + "' with value " + value);
            }
        }
        return nameValue;
    }
}
