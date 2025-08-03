/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Parcel;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class RcsContactUceCapabilityTest {

    private static final Uri TEST_CONTACT = Uri.fromParts("sip", "test1", null);

    public static final String FEATURE_TAG_CHAT_IM =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.im\"";

    public static final String FEATURE_TAG_CHAT_SESSION =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";

    public static final String FEATURE_TAG_FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";

    public static final String FEATURE_TAG_POST_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.callunanswered\"";

    @Test
    public void testParcelUnparcel() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Create two presence tuples for testing.
        RcsContactPresenceTuple mmtelTuple = createPresenceMmtelTuple();
        RcsContactPresenceTuple ftTuple = createPresenceFtTuple();

        PresenceBuilder presenceBuilder = new PresenceBuilder(TEST_CONTACT,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(mmtelTuple);
        presenceBuilder.addCapabilityTuples(Collections.singletonList(ftTuple));
        presenceBuilder.setEntityUri(TEST_CONTACT);

        final RcsContactUceCapability testCapability = presenceBuilder.build();

        // parcel and unparcel
        Parcel infoParceled = Parcel.obtain();
        testCapability.writeToParcel(infoParceled, 0);
        infoParceled.setDataPosition(0);
        RcsContactUceCapability unparceledCapability =
                RcsContactUceCapability.CREATOR.createFromParcel(infoParceled);
        infoParceled.recycle();

        assertEquals(unparceledCapability.getContactUri(), testCapability.getContactUri());
        assertEquals(unparceledCapability.getSourceType(), testCapability.getSourceType());
        assertEquals(unparceledCapability.getRequestResult(), testCapability.getRequestResult());
        assertEquals(unparceledCapability.getEntityUri(), testCapability.getEntityUri());
        assertEquals(unparceledCapability.getCapabilityMechanism(),
                testCapability.getCapabilityMechanism());

        // Verify mmtel tuple
        RcsContactPresenceTuple unparceledMMtelTuple =
                unparceledCapability.getCapabilityTuple(RcsContactPresenceTuple.SERVICE_ID_MMTEL);
        verifyUnparceledTuple(unparceledMMtelTuple, mmtelTuple);

        // Verify File transfer tuple
        RcsContactPresenceTuple unparceledFtTuple =
                unparceledCapability.getCapabilityTuple(RcsContactPresenceTuple.SERVICE_ID_FT);
        verifyUnparceledTuple(unparceledFtTuple, ftTuple);

        // Verify all the tuples from the API getCapabilityTuples
        List<RcsContactPresenceTuple> unparceledTuples = unparceledCapability.getCapabilityTuples();
        assertNotNull(unparceledTuples);
        assertEquals(2, unparceledTuples.size());
        for (RcsContactPresenceTuple unparcelTuple : unparceledTuples) {
            String serverId = unparcelTuple.getServiceId();
            if (RcsContactPresenceTuple.SERVICE_ID_MMTEL.equals(serverId)) {
                verifyUnparceledTuple(unparcelTuple, mmtelTuple);
            } else if (RcsContactPresenceTuple.SERVICE_ID_FT.equals(serverId)) {
                verifyUnparceledTuple(unparcelTuple, ftTuple);
            } else {
                fail("Invalid service ID: " + serverId);
            }
        }
    }

    private RcsContactPresenceTuple createPresenceMmtelTuple() {
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(true, true);
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tupleBuilder.setContactUri(TEST_CONTACT)
                .setTime(Instant.now())
                .setServiceDescription("service description for contact 1")
                .setServiceCapabilities(servCapsBuilder.build());
        return tupleBuilder.build();
    }

    private RcsContactPresenceTuple createPresenceFtTuple() {
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(true, true);
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_FT, "1.0");
        tupleBuilder.setContactUri(TEST_CONTACT)
                .setServiceDescription("service description for contact2")
                .setServiceCapabilities(servCapsBuilder.build());
        return tupleBuilder.build();
    }

    private void verifyUnparceledTuple(RcsContactPresenceTuple unparceledTuple,
            RcsContactPresenceTuple expectedTuple) {
        assertNotNull(unparceledTuple);
        assertEquals(unparceledTuple.getStatus(), expectedTuple.getStatus());
        assertEquals(unparceledTuple.getServiceId(), expectedTuple.getServiceId());
        assertEquals(unparceledTuple.getServiceDescription(),
                expectedTuple.getServiceDescription());
        assertEquals(unparceledTuple.getServiceVersion(), expectedTuple.getServiceVersion());
        assertEquals(unparceledTuple.getContactUri(), expectedTuple.getContactUri());
        assertEquals(unparceledTuple.getTime(), expectedTuple.getTime());

        ServiceCapabilities unparceledServiceCaps = unparceledTuple.getServiceCapabilities();
        ServiceCapabilities expectedServiceCaps = unparceledTuple.getServiceCapabilities();
        assertNotNull(unparceledServiceCaps);
        assertEquals(unparceledServiceCaps.isAudioCapable(), expectedServiceCaps.isAudioCapable());
        assertEquals(unparceledServiceCaps.isVideoCapable(), expectedServiceCaps.isVideoCapable());

        List<String> unparceledDuplexModes = unparceledServiceCaps.getSupportedDuplexModes();
        List<String> expectedDuplexModes = expectedServiceCaps.getSupportedDuplexModes();
        assertEquals(unparceledDuplexModes.size(), expectedDuplexModes.size());
        assertTrue(unparceledDuplexModes.containsAll(expectedDuplexModes));

        List<String> unparceledUnsupportedModes = unparceledServiceCaps.getUnsupportedDuplexModes();
        List<String> expectedUnsupportedModes = expectedServiceCaps.getUnsupportedDuplexModes();
        assertEquals(unparceledUnsupportedModes.size(), expectedUnsupportedModes.size());
        assertTrue(unparceledUnsupportedModes.containsAll(expectedUnsupportedModes));
    }

    @Test
    public void testParcelUnparcelForOptions() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        final Set<String> featureTags = new HashSet<>();
        featureTags.add(FEATURE_TAG_CHAT_IM);
        featureTags.add(FEATURE_TAG_CHAT_SESSION);
        featureTags.add(FEATURE_TAG_FILE_TRANSFER);

        // Create an OptionsBuilder instance through the constructor OptionsBuilder(Uri, int)
        OptionsBuilder optionsBuilder = new OptionsBuilder(TEST_CONTACT,
                RcsContactUceCapability.SOURCE_TYPE_CACHED);
        optionsBuilder.addFeatureTags(featureTags);
        optionsBuilder.addFeatureTag(FEATURE_TAG_POST_CALL);
        optionsBuilder.setRequestResult(RcsContactUceCapability.REQUEST_RESULT_FOUND);
        RcsContactUceCapability testCapability = optionsBuilder.build();

        // parcel and unparcel
        Parcel infoParceled = Parcel.obtain();
        testCapability.writeToParcel(infoParceled, 0);
        infoParceled.setDataPosition(0);
        RcsContactUceCapability unparceledCapability =
                RcsContactUceCapability.CREATOR.createFromParcel(infoParceled);
        infoParceled.recycle();

        // Verify the unparceled capability
        verifyUnparceledCapability(testCapability, unparceledCapability);

        // Create an OptionsBuilder instance through the constructor OptionsBuilder(Uri)
        optionsBuilder = new OptionsBuilder(TEST_CONTACT);
        optionsBuilder.addFeatureTags(featureTags);
        optionsBuilder.addFeatureTag(FEATURE_TAG_POST_CALL);
        optionsBuilder.setRequestResult(RcsContactUceCapability.REQUEST_RESULT_FOUND);
        testCapability = optionsBuilder.build();

        // parcel and unparcel
        infoParceled = Parcel.obtain();
        testCapability.writeToParcel(infoParceled, 0);
        infoParceled.setDataPosition(0);
        unparceledCapability = RcsContactUceCapability.CREATOR.createFromParcel(infoParceled);
        infoParceled.recycle();

        // Verify the unparceled capability
        verifyUnparceledCapability(testCapability, unparceledCapability);
    }

    private void verifyUnparceledCapability(RcsContactUceCapability expectedCap,
            RcsContactUceCapability unparceledCapability) {
        assertEquals(expectedCap.getContactUri(), unparceledCapability.getContactUri());
        assertEquals(expectedCap.getSourceType(), unparceledCapability.getSourceType());
        assertEquals(expectedCap.getRequestResult(), unparceledCapability.getRequestResult());
        assertEquals(expectedCap.getEntityUri(), unparceledCapability.getEntityUri());
        assertEquals(expectedCap.getCapabilityMechanism(),
                unparceledCapability.getCapabilityMechanism());

        Set<String> expectedFeatureTags = expectedCap.getFeatureTags();
        Set<String> unparceledFeatureTags = unparceledCapability.getFeatureTags();
        assertEquals(expectedFeatureTags.size(), unparceledFeatureTags.size());
        Iterator<String> expectedFeatureTag = expectedFeatureTags.iterator();
        while (expectedFeatureTag.hasNext()) {
            assertTrue(unparceledFeatureTags.contains(expectedFeatureTag.next()));
        }
    }
}
