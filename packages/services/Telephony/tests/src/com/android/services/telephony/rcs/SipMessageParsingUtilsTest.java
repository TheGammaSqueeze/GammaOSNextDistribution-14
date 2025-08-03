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

package com.android.services.telephony.rcs;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.SipMessageParsingUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SipMessageParsingUtilsTest {

    @Test
    public void testNoAcceptContactHeader() {
        String header = "Via: SIP/2.0/UDP ex.place.com;branch=z9hG4bK776asdhds";
        assertTrue(SipMessageParsingUtils.getAcceptContactFeatureTags(header).isEmpty());
    }

    @Test
    public void testAcceptContactHeaderMultipleValues() {
        Set<String> testFeatures = new ArraySet<>(2);
        // "+a="b,c" should be split into +a="b" and +a="c"
        testFeatures.add("+a=\"b,c\"");
        testFeatures.add("+d");
        testFeatures.add("+e=\"f\"");
        // These non-feature tags should be filtered out.
        testFeatures.add("video");
        testFeatures.add("blah=BLAH");
        String header = "Via: SIP/2.0/UDP ex.place.com;branch=z9hG4bK776asdhds";
        header = addFeatures(header, testFeatures);
        Set<String> features = SipMessageParsingUtils.getAcceptContactFeatureTags(header);
        assertNotNull(features);
        assertEquals(4, features.size());
        assertTrue(features.containsAll(Arrays.asList("+a=\"b\"", "+a=\"c\"", "+d", "+e=\"f\"")));
    }

    private String addFeatures(String headers, Set<String> features) {
        StringBuilder newHeader = new StringBuilder(headers + "\n");
        newHeader.append("Accept-Contact:*");
        for (String feature : features) {
            newHeader.append(";").append(feature);
        }
        return newHeader.toString();
    }
}
