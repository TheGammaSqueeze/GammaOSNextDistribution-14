/*
 * Copyright (C) 2023 The Android Open Source Project
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

package libcore.java.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;


@RunWith(JUnit4.class)
public class PropertiesTest {

    @Test
    public void storeToXml_withCharset_andBack() throws Exception {
        Properties props = new Properties();

        props.setProperty("key", "value 1&-\n\t sadfbo");
        props.setProperty("ключ", "значение 123-&\n\t ттффваи");
        props.setProperty("key1", "value1");
        props.setProperty("key2", "value2");
        props.setProperty("key3", "value3");
        props.setProperty("<a>key4</a>", "\"value4");
        props.setProperty("key5   ", "<h>value5</h>");
        props.setProperty("<a>key6</a>", "   <h>value6</h>   ");
        props.setProperty("<comment>key7</comment>", "value7");
        props.setProperty("  key8   ", "<comment>value8</comment>");
        props.setProperty("&lt;key9&gt;", "'value9");
        props.setProperty("key10\"", "&lt;value10&gt;");
        props.setProperty("&amp;key11&amp;", "value11");
        props.setProperty("key12", "&amp;value12&amp;");
        props.setProperty("<a>&amp;key13&lt;</a>",
                "&amp;&value13<b>&amp;</b>");

        roundtripTest(props, StandardCharsets.UTF_8);
        roundtripTest(props, StandardCharsets.UTF_16);
        roundtripTest(props, Charset.forName("ISO-8859-1"));
        assertThrows(NullPointerException.class,
                () -> props.storeToXML(new ByteArrayOutputStream(), "comment", (Charset) null));
    }

    private static void roundtripTest(Properties props, Charset charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.storeToXML(out, "comment", charset);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        Properties deserialised = new Properties();
        deserialised.loadFromXML(in);

        assertEquals(props.stringPropertyNames(), deserialised.stringPropertyNames());

        for (String key : props.stringPropertyNames()) {
            assertEquals("value for " + key, props.getProperty(key), deserialised.getProperty(key));
        }
    }
}
