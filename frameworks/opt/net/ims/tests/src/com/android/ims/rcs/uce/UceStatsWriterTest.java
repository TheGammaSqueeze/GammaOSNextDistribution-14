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

package com.android.ims.rcs.uce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;

import com.android.ims.rcs.uce.UceStatsWriter;
import com.android.ims.rcs.uce.UceStatsWriter.UceStatsCallback;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class UceStatsWriterTest {
    private int mSubId = 3;
    private long mTaskId = 5;
    private int mRegistrationTech = 3;
    private int mType = 2;
    private boolean mSuccessful = true;
    private int mCommandCode = 4;
    private int mNetworkResponse = 200;
    private String mReason = "noresource";

    private Callback mCallback;
    private TestableUceStatsWriter mWrite;

    class Callback implements UceStatsCallback {
        int subId;
        List<String> featureTagList;
        List<String> serviceIdList;
        List<String> versions;
        int registrationTech;
        int type;
        boolean successful;
        int commandCode;
        int networkResponse;
        long taskId;
        List<RcsContactUceCapability> updatedCapList;
        String reason;

        Callback() {
        }

        public void onImsRegistrationFeatureTagStats(int subId, List<String> featureTagList,
            int registrationTech) {
            this.subId = subId;
            this.featureTagList = featureTagList;
            this.registrationTech = registrationTech;
        }

        public void onStoreCompleteImsRegistrationFeatureTagStats(int subId) {}

        public void onImsRegistrationServiceDescStats(int subId, List<String> serviceIdList,
            List<String> serviceIdVersionList, int registrationTech) {
            this.subId = subId;
            this.serviceIdList = serviceIdList;
            this.versions = serviceIdVersionList;
            this.registrationTech = registrationTech;
        }

        public void onSubscribeResponse(int subId, long taskId, int networkResponse) {
            this.subId = subId;
            this.taskId = taskId;
            this.successful = true;
            this.commandCode = 0;
            this.networkResponse = networkResponse;
        }

        public void onUceEvent(int subId, int type, boolean successful, int commandCode,
            int networkResponse) {
            this.subId = subId;
            this.type = type;
            this.successful = successful;
            this.commandCode = commandCode;
            this.networkResponse = networkResponse;
        }

        public void onSubscribeTerminated(int subId, long taskId, String reason) {
            this.subId = subId;
            this.taskId = taskId;
            this.reason = reason;
        }

        public void onPresenceNotifyEvent(int subId, long taskId,
            List<RcsContactUceCapability> updatedCapList) {
            this.subId = subId;
            this.taskId = taskId;
            this.updatedCapList = updatedCapList;
        }

        public void onStoreCompleteImsRegistrationServiceDescStats(int subId) {
            this.subId = subId;
        }
    }

    private class TestableUceStatsWriter extends UceStatsWriter {
        public TestableUceStatsWriter(UceStatsCallback callback) {
            super(callback);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mCallback = new Callback();
        mWrite = new TestableUceStatsWriter(mCallback);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    @SmallTest
    public void setImsRegistrationFeatureTagStats() throws Exception {
        List<String> featureTags = new ArrayList<>();
        featureTags.add("+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcse.im\"");
        featureTags.add("+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"");
        featureTags.add("+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.ftsms\"");
        mWrite.setImsRegistrationFeatureTagStats(mSubId, featureTags, mRegistrationTech);
        assertEquals(mSubId, mCallback.subId);
        for (int index = 0; index < featureTags.size(); index++) {
            assertEquals(featureTags.get(index), mCallback.featureTagList.get(index));
        }
        assertEquals(mRegistrationTech, mCallback.registrationTech);
    }

    @Test
    @SmallTest
    public void setImsRegistrationServiceDescStats() throws Exception {
        List<RcsContactPresenceTuple> tupleList = new ArrayList<>();
        tupleList.add(getContactChatTuple());
        tupleList.add(getContactFtTuple());
        tupleList.add(getContactUnknown1Tuple());
        tupleList.add(getContactUnknown2Tuple());
        mWrite.setImsRegistrationServiceDescStats(mSubId, tupleList, mRegistrationTech);
        assertEquals(mSubId, mCallback.subId);
        for (int index = 0; index < tupleList.size(); index++) {
            assertEquals(tupleList.get(index).getServiceId(), mCallback.serviceIdList.get(index));
            assertEquals(tupleList.get(index).getServiceVersion(), mCallback.versions.get(index));
        }
        assertEquals(mRegistrationTech, mCallback.registrationTech);
    }

    @Test
    @SmallTest
    public void setSubscribeEvent() throws Exception {
        mWrite.setSubscribeResponse(mSubId, mTaskId, mNetworkResponse);
        assertEquals(mSubId, mCallback.subId);
        assertEquals(mTaskId, mCallback.taskId);
        assertTrue(mCallback.successful);
        assertEquals(0, mCallback.commandCode);
        assertEquals(mNetworkResponse, mCallback.networkResponse);
    }

    @Test
    @SmallTest
    public void setSubscribeTerminated() throws Exception {
        mWrite.setSubscribeResponse(mSubId, mTaskId, mNetworkResponse);
        mWrite.setSubscribeTerminated(mSubId, mTaskId, mReason);
        assertEquals(mSubId, mCallback.subId);
        assertEquals(mTaskId, mCallback.taskId);
        assertEquals(mReason, mCallback.reason);
    }

    @Test
    @SmallTest
    public void setUceEvent() throws Exception {
        mWrite.setUceEvent(mSubId, mType, mSuccessful, mCommandCode, mNetworkResponse);
        assertEquals(mSubId, mCallback.subId);
        assertEquals(mType, mCallback.type);
        assertEquals(mSuccessful, mCallback.successful);
        assertEquals(mCommandCode, mCallback.commandCode);
        assertEquals(mNetworkResponse, mCallback.networkResponse);
    }

    @Test
    @SmallTest
    public void setPresenceNotifyEvent() throws Exception {
        List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactUceCapability.PresenceBuilder builder =
            new RcsContactUceCapability.PresenceBuilder(null, 0, 2);
        builder.addCapabilityTuple(getContactChatTuple());
        builder.addCapabilityTuple(getContactCallComposer2Tuple());
        builder.addCapabilityTuple(getContactUnknown1Tuple());
        updatedCapList.add(builder.build());

        mWrite.setPresenceNotifyEvent(mSubId, mTaskId, updatedCapList);
        assertEquals(mSubId, mCallback.subId);
        assertEquals(mTaskId, mCallback.taskId);
        assertEquals(updatedCapList.size(), mCallback.updatedCapList.size());
        for (int index = 0; index < updatedCapList.size(); index++) {
            RcsContactUceCapability input = updatedCapList.get(index);
            RcsContactUceCapability output = mCallback.updatedCapList.get(index);
            assertEquals(input.getCapabilityTuples().size(), output.getCapabilityTuples().size());
        }
    }

    @Test
    @SmallTest
    public void setPresenceNotifyEvent_withCallComposer2Caps() throws Exception {
        RcsContactPresenceTuple tuple = getContactCallComposer2Tuple();
        List<RcsContactUceCapability> updatedCapList = new ArrayList<>();
        RcsContactUceCapability.PresenceBuilder builder =
            new RcsContactUceCapability.PresenceBuilder(null, 0, 2);
        builder.addCapabilityTuple(getContactCallComposer2Tuple());
        updatedCapList.add(builder.build());

        mWrite.setPresenceNotifyEvent(mSubId, mTaskId, updatedCapList);
        assertEquals(mSubId, mCallback.subId);
        assertEquals(mTaskId, mCallback.taskId);
        assertEquals(updatedCapList.size(), mCallback.updatedCapList.size());
    }

    @Test
    @SmallTest
    public void setUnPublish() throws Exception {
        mWrite.setUnPublish(mSubId);
        assertEquals(mSubId, mCallback.subId);
    }

    private RcsContactPresenceTuple getContactChatTuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", RcsContactPresenceTuple.SERVICE_ID_CHAT_V1,
                "1.0");
        return builder.build();
    }

    private RcsContactPresenceTuple getContactMmtelTuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", RcsContactPresenceTuple.SERVICE_ID_MMTEL,
                "1.0");
        return builder.build();
    }

    private RcsContactPresenceTuple getContactFtTuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", RcsContactPresenceTuple.SERVICE_ID_FT,
                "1.0");
        return builder.build();
    }

    private RcsContactPresenceTuple getContactCallComposer2Tuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open",
                RcsContactPresenceTuple.SERVICE_ID_CALL_COMPOSER,
                "2.0");
        return builder.build();
    }

    private RcsContactPresenceTuple getContactUnknown1Tuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", "Unknown1",
                "8.0");
        return builder.build();
    }

    private RcsContactPresenceTuple getContactUnknown2Tuple() {
        RcsContactPresenceTuple.Builder builder =
            new RcsContactPresenceTuple.Builder("open", "Unknown2",
                "9.0");
        return builder.build();
    }
}
