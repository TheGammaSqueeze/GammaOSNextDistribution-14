/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.telephony.qns;

import static org.mockito.Mockito.spy;

import android.content.Context;
import android.telephony.Rlog;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

@RunWith(JUnit4.class)
public class ConfigXmlTest {

    @Mock private Context mContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
    }

    protected static void slog(String log) {
        Rlog.d(ConfigXmlTest.class.getSimpleName(), log);
    }

    @Test
    public void testNumValueInConfigArray() {
        try {
            String[] configName = mContext.getAssets().list("");
            if (configName == null) {
                return;
            }

            for (String fileName : configName) {
                if (fileName.startsWith("carrier_config_carrierid_")) {
                    InputStream input = mContext.getAssets().open(fileName);
                    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                    builderFactory.setNamespaceAware(true);
                    DocumentBuilder builder = builderFactory.newDocumentBuilder();
                    Document doc = builder.parse(input);

                    String queryIntArray = "//int-array[@num!=count(./item)]";
                    String queryStringArray = "//string-array[@num!=count(./item)]";
                    if (!verifyWithXmlQuery(doc, queryIntArray)
                            || !verifyWithXmlQuery(doc, queryStringArray)) {
                        Assert.fail("ConfigXmlTest has failed at " + fileName);
                    }
                }
            }
        } catch (Exception e) {
            Assert.fail("ConfigXmlTest has an exception : " + e);
        }
    }

    private boolean verifyWithXmlQuery(Document doc, String queryIntArray)
            throws XPathExpressionException, TransformerException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xpath.evaluate(queryIntArray, doc, XPathConstants.NODESET);
        if (nodeList == null || nodeList.getLength() <= 0) {
            return true;
        }
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            StringWriter buf = new StringWriter();
            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.INDENT, "yes");
            xform.transform(new DOMSource(node), new StreamResult(buf));
            slog("wrong num value : " + buf);
        }
        return false;
    }
}
