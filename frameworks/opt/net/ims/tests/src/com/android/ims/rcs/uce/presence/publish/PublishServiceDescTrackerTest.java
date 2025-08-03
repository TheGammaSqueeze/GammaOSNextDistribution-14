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

package com.android.ims.rcs.uce.presence.publish;

import static org.junit.Assert.assertEquals;

import android.telephony.ims.RcsContactPresenceTuple;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.rcs.uce.util.FeatureTags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PublishServiceDescTrackerTest {

    public static final ServiceDescription TEST_SERVICE_DESC_1 = new ServiceDescription(
            "org.test.test1", "1.0", "ABC");
    public static final ServiceDescription TEST_SERVICE_DESC_2 = new ServiceDescription(
            "org.test.test1", "2.0", "DEF");
    public static final ServiceDescription TEST_OVERRIDE_MMTEL_DESC = new ServiceDescription(
            RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0", "ABC");

    public static final String TEST_FEATURE_TAG_1 = "+g.test.test1=\"testing\"";
    public static final String TEST_FEATURE_TAG_2A = "+g.test.test2=\"testing\"";
    public static final String TEST_FEATURE_TAG_2B = "+g.test.testAdd";

    public static final String[] TEST_OVERRIDE_CONFIG = new String[] {
            "org.test.test1|1.0|ABC|+g.test.test1=\"testing\"",
            "org.test.test1|2.0|DEF|+g.test.test2=\"testing\";+g.test.testAdd",
            "org.test.test3|1.0|ABC|+g.test.test3",
            // Modify MMTEL+video to have a different description
            "org.3gpp.urn:urn-7:3gpp-service.ims.icsi.mmtel|1.0|ABC|+g.3gpp.icsi-ref=\""
                    + "urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\";video"
    };

    public static final String TEST_FEATURE_TAG_FT_FORMAT =
            " +g.3gpp.iari-ref =  \" urn%3AuRN-7%3A3gpp-applicatION.ims.iari.rcs.fthttp \"   ";

    public static final String TEST_FEATURE_TAG_FT_SMS_FORMAT =
            " +g.3gpp.iari-ref=\"    urn%3Aurn-7%3A3gpp-application.iMS.iari.rcs.ftsms    \"";

    public static final String TEST_FEATURE_TAG_CHATBOT_FORMAT =
            " +g.3gpp.iari-ref=  \" Urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot \"";

    public static final String TEST_FEATURE_TAG_BOTVERSION_V2_FORMAT =
            "+g.gsma.rcs.botVersion =\" #=1  , #=2 \"    ";

    public static final String TEST_FEATURE_TAG_BOTVERSION_FORMAT =
            "+g.gsma.rcs.botVersion =\" #=1   \"    ";

    public static final String TEST_FEATURE_TAG_MMTEL_FORMAT =
            "  +g.3gpp.icsi-ref =    \"urn%3Aurn-7%3A3gpp-servIce.ims.icsi.mmtel \"   ";

    public static final String TEST_FEATURE_TAG_VIDEO_FORMAT = " VIDEO  ";

    public static final String[] TEST_OVERRIDE_CONFIG_FORMAT = new String[] {
            " org.test.test1 | 1.0 | ABC | +g.test.tEST1= \" testing  \"         ",
            "  org.test.test1 |2.0  |DEF | +g.teSt.test2 = \"testing\" ;  +g.test.testAdd  ",
            " org.test.test3  | 1.0   |ABC| +g.TEst.test3   ",
            // Modify MMTEL+video to have a different description
            " org.3gpp.urn:urn-7:3gpp-service.ims.icsi.mmtel |   1.0 |  ABC |  +g.3gPp.icsi-ref=\""
                    + "urn%3Aurn-7%3A3gpp-seRVice.ims.icsi.mmtel   \" ;  video   "
    };

    @SmallTest
    @Test
    public void testDefaultConfigMatch() {
        PublishServiceDescTracker t1 =
                PublishServiceDescTracker.fromCarrierConfig(new String[]{});

        Set<ServiceDescription> expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_FT);
        Set<String> imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_FT,
                ServiceDescription.SERVICE_DESCRIPTION_FT_SMS));
        imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                FeatureTags.FEATURE_TAG_FILE_TRANSFER_VIA_SMS);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_SUPPORTED);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Should see chatbot v1 and v2 pop up in this case (same FTs)
        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION_V1,
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION_V2));
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_V2_SUPPORTED);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE);
        imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_MMTEL);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE_VIDEO);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_MMTEL,
                FeatureTags.FEATURE_TAG_VIDEO);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_SLM);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_PAGER_MODE,
                FeatureTags.FEATURE_TAG_LARGE_MODE,
                FeatureTags.FEATURE_TAG_DEFERRED_MESSAGING,
                FeatureTags.FEATURE_TAG_LARGE_PAGER_MODE);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());


        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_SLM_PAGER_LARGE);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_PAGER_MODE,
                FeatureTags.FEATURE_TAG_LARGE_MODE);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // delete the feature tags for Unregistered
        expectedSet = new ArraySet<>();
        t1.updateImsRegistration(Collections.emptySet());
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());
    }

    @SmallTest
    @Test
    public void testOverrideCarrierConfigMatch() {
        PublishServiceDescTracker t1 =
                PublishServiceDescTracker.fromCarrierConfig(TEST_OVERRIDE_CONFIG);

        Set<ServiceDescription> expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_FT);
        Set<String> imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_FT, TEST_SERVICE_DESC_1));
        imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                TEST_FEATURE_TAG_1);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test overrides also allow for multiple FT specifications
        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_CALL_COMPOSER_MMTEL, TEST_SERVICE_DESC_2));
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_CALL_COMPOSER_VIA_TELEPHONY,
                TEST_FEATURE_TAG_2B, TEST_FEATURE_TAG_2A);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test override does not affect mmtel voice
        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE);
        imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_MMTEL);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test override description works for existing tags
        expectedSet =  Collections.singleton(TEST_OVERRIDE_MMTEL_DESC);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_MMTEL,
                FeatureTags.FEATURE_TAG_VIDEO);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());
    }

    @SmallTest
    @Test
    public void testNonstandardImsRegFormatMatch() {
        PublishServiceDescTracker t1 =
                PublishServiceDescTracker.fromCarrierConfig(new String[]{});

        Set<ServiceDescription> expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_FT,
                ServiceDescription.SERVICE_DESCRIPTION_FT_SMS));
        Set<String> imsReg = createImsRegistration(TEST_FEATURE_TAG_FT_FORMAT,
                TEST_FEATURE_TAG_FT_SMS_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // The registered tag is botVersion=#1,
        // so check if the service description of chatbot version 1.0 should be set.
        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION);
        imsReg = createImsRegistration(
                TEST_FEATURE_TAG_CHATBOT_FORMAT,
                TEST_FEATURE_TAG_BOTVERSION_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // The registered tag is botVersion=#1,#2,
        // so check if the service description of chatbot version 1.0 and 2.0 should be set.
        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION_V1,
                ServiceDescription.SERVICE_DESCRIPTION_CHATBOT_SESSION_V2));
        imsReg = createImsRegistration(
                TEST_FEATURE_TAG_CHATBOT_FORMAT,
                TEST_FEATURE_TAG_BOTVERSION_V2_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE);
        imsReg = createImsRegistration(TEST_FEATURE_TAG_MMTEL_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE_VIDEO);
        imsReg = createImsRegistration(
                TEST_FEATURE_TAG_MMTEL_FORMAT,
                TEST_FEATURE_TAG_VIDEO_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());
    }

    @SmallTest
    @Test
    public void testOverrideCarrierConfigNonstandardFormatMatch() {
        PublishServiceDescTracker t1 =
                PublishServiceDescTracker.fromCarrierConfig(TEST_OVERRIDE_CONFIG_FORMAT);

        Set<ServiceDescription> expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_FT);
        Set<String> imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_FT, TEST_SERVICE_DESC_1));
        imsReg = createImsRegistration(FeatureTags.FEATURE_TAG_FILE_TRANSFER,
                TEST_FEATURE_TAG_1);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test overrides also allow for multiple FT specifications
        expectedSet = new ArraySet<>(Arrays.asList(
                ServiceDescription.SERVICE_DESCRIPTION_CALL_COMPOSER_MMTEL, TEST_SERVICE_DESC_2));
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_CALL_COMPOSER_VIA_TELEPHONY,
                TEST_FEATURE_TAG_2B, TEST_FEATURE_TAG_2A);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test override does not affect mmtel voice
        expectedSet = Collections.singleton(
                ServiceDescription.SERVICE_DESCRIPTION_MMTEL_VOICE);
        imsReg = createImsRegistration(TEST_FEATURE_TAG_MMTEL_FORMAT);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());

        // Test override description works for existing tags
        expectedSet =  Collections.singleton(TEST_OVERRIDE_MMTEL_DESC);
        imsReg = createImsRegistration(
                FeatureTags.FEATURE_TAG_MMTEL,
                FeatureTags.FEATURE_TAG_VIDEO);
        t1.updateImsRegistration(imsReg);
        assertEquals(expectedSet, t1.copyRegistrationCapabilities());
    }

    private Set<String> createImsRegistration(String... imsReg) {
        return new ArraySet<>(imsReg);
    }
}
