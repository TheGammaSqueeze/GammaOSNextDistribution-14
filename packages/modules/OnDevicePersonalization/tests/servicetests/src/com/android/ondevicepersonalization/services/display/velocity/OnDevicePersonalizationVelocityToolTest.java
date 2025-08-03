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

package com.android.ondevicepersonalization.services.display.velocity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.owasp.encoder.Encode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationVelocityToolTest {
    private static final List<String> sTestStrings = new ArrayList<>(Arrays.asList(
            "<script>",
            "&&&&&&&&&&&&&&&&&&&&",
            "safe",
            null
    ));

    @Test
    public void testEncodeHtml() {
        for (String s : sTestStrings) {
            assertEquals(Encode.forHtml(s), OnDevicePersonalizationVelocityTool.encodeHtml(s));
        }
    }

    @Test
    public void testEncodeUrl() {
        for (String s : sTestStrings) {
            assertEquals(Encode.forUriComponent(s),
                    OnDevicePersonalizationVelocityTool.encodeUrl(s));
        }
    }

    @Test
    public void testEncodeJs() {
        for (String s : sTestStrings) {
            assertEquals(Encode.forJavaScript(s), OnDevicePersonalizationVelocityTool.encodeJs(s));
        }
    }

    @Test
    public void testEncodeCssString() {
        for (String s : sTestStrings) {
            assertEquals(Encode.forCssString(s),
                    OnDevicePersonalizationVelocityTool.encodeCssString(s));
        }
    }

    @Test
    public void testEncodeCssUrl() {
        for (String s : sTestStrings) {
            assertEquals(Encode.forCssUrl(s), OnDevicePersonalizationVelocityTool.encodeCssUrl(s));
        }
    }
}
