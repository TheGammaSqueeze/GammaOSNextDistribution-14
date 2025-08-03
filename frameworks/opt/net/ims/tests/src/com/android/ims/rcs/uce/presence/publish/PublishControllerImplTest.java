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

package com.android.ims.rcs.uce.presence.publish;

import static com.android.ims.rcs.uce.presence.publish.PublishController.PUBLISH_TRIGGER_RETRY;
import static com.android.ims.rcs.uce.presence.publish.PublishController.PUBLISH_TRIGGER_VT_SETTING_CHANGE;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.SipDetails;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController;
import com.android.ims.rcs.uce.UceDeviceState.DeviceStateResult;
import com.android.ims.rcs.uce.UceStatsWriter;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl.DeviceCapListenerFactory;
import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl.PublishProcessorFactory;
import com.android.ims.ImsTestBase;

import java.time.Instant;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class PublishControllerImplTest extends ImsTestBase {

    @Mock RcsFeatureManager mFeatureManager;
    @Mock PublishProcessor mPublishProcessor;
    @Mock PublishProcessorFactory mPublishProcessorFactory;
    @Mock DeviceCapabilityListener mDeviceCapListener;
    @Mock DeviceCapListenerFactory mDeviceCapListenerFactory;
    @Mock UceController.UceControllerCallback mUceCtrlCallback;
    @Mock RemoteCallbackList<IRcsUcePublishStateCallback> mPublishStateCallbacks;
    @Mock DeviceStateResult mDeviceStateResult;
    @Mock IRcsUcePublishStateCallback mIRcsUcePublishStateCallback;
    @Mock UceStatsWriter mUceStatsWriter;

    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mPublishProcessor).when(mPublishProcessorFactory).createPublishProcessor(any(),
                eq(mSubId), any(), any());
        doReturn(mDeviceCapListener).when(mDeviceCapListenerFactory).createDeviceCapListener(any(),
                eq(mSubId), any(), any(), any());
        doReturn(mDeviceStateResult).when(mUceCtrlCallback).getDeviceState();
        doReturn(false).when(mDeviceStateResult).isRequestForbidden();
        doReturn(false).when(mDeviceStateResult).isPublishRequestBlocked();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRcsConnected() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.onRcsConnected(mFeatureManager);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        verify(mPublishProcessor).onRcsConnected(mFeatureManager);
    }

    @Test
    @SmallTest
    public void testRcsDisconnected() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.onRcsDisconnected();
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        verify(mPublishProcessor).onRcsDisconnected();
    }

    @Test
    @SmallTest
    public void testDestroyed() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.onDestroy();

        verify(mPublishProcessor, never()).doPublish(anyInt());
    }

    @Test
    @SmallTest
    public void testGetPublishStateWithNotSupportPublishingState() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        int initState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, initState);

        publishController.getPublishControllerCallback().updatePublishRequestResult(
                RcsUceAdapter.PUBLISH_STATE_OK, Instant.now(), null, null);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        int latestState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, latestState);
    }

    @Test
    @SmallTest
    public void testGetPublishStateWithSupportPublishingState() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        int initState = publishController.getUcePublishState(true);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, initState);

        publishController.getPublishControllerCallback().updatePublishRequestResult(
                RcsUceAdapter.PUBLISH_STATE_PUBLISHING, Instant.now(), null, null);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        int latestState = publishController.getUcePublishState(true);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, latestState);
    }

    @Test
    @SmallTest
    public void testRegisterPublishStateCallback() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.registerPublishStateCallback(mIRcsUcePublishStateCallback, true);

        verify(mPublishStateCallbacks).register(any(), any());
    }

    @Test
    @SmallTest
    public void testUnregisterPublishStateCallback() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.unregisterPublishStateCallback(mIRcsUcePublishStateCallback);

        verify(mPublishStateCallbacks).unregister(any());
    }

    @Test
    @SmallTest
    public void testUnpublish() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        //To initialize the public state to publish_ok.
        publishController.setCapabilityType(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);

        publishController.onUnpublish();

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        int publishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        verify(mPublishProcessor).resetState();
        verify(mUceStatsWriter).setUnPublish(eq(mSubId));
    }

    @Test
    @SmallTest
    public void testImsUnregistered() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        //To initialize the public state to publish_ok.
        publishController.setCapabilityType(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);

        // Trigger a ims unregistered
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.updateImsUnregistered();

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        int publishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        verify(mPublishProcessor).resetState();
        verify(mUceStatsWriter).setUnPublish(eq(mSubId));
    }

    @Test
    @SmallTest
    public void testPublishUpdated() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        int responseCode = 200;
        String responsePhrase = "OK";

        publishController.onPublishUpdated(new SipDetails.Builder(SipDetails.METHOD_PUBLISH)
                .setSipResponseCode(responseCode, responsePhrase).build());

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        ArgumentCaptor<PublishRequestResponse> captor =
                ArgumentCaptor.forClass(PublishRequestResponse.class);

        verify(mPublishProcessor).publishUpdated(captor.capture());
        PublishRequestResponse response = captor.getValue();
        int expectedCode = response.getNetworkRespSipCode().orElse(-1);
        String expectedPhrase = response.getReasonPhrase().orElse("");
        assertEquals(responseCode, expectedCode);
        assertEquals(responsePhrase, expectedPhrase);
        SipDetails details = response.getSipDetails().orElse(null);
        assertNotNull(details);
        assertEquals(responseCode, details.getResponseCode());
        assertEquals(responsePhrase, details.getResponsePhrase());
    }

    @Test
    @SmallTest
    public void testPublishUpdatedWithSipDetails() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        int responseCode = 200;
        String responsePhrase = "OK";
        int reasonHdrCause = 7;
        String reasonHdrText = "reasonHeaderText";
        int cseq = 10;
        String callId = "TestCallId";

        publishController.onPublishUpdated(new SipDetails.Builder(SipDetails.METHOD_PUBLISH)
                .setCSeq(cseq).setSipResponseCode(responseCode, responsePhrase).setCallId(callId)
                .setSipResponseReasonHeader(reasonHdrCause, reasonHdrText).build());

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        ArgumentCaptor<PublishRequestResponse> captor =
                ArgumentCaptor.forClass(PublishRequestResponse.class);

        verify(mPublishProcessor).publishUpdated(captor.capture());
        PublishRequestResponse response = captor.getValue();
        int expectedCode = response.getNetworkRespSipCode().orElse(-1);
        String expectedPhrase = response.getReasonPhrase().orElse("");
        int expectedReasonCause = response.getReasonHeaderCause().orElse(-1);
        String expectedReasonText = response.getReasonHeaderText().orElse("");

        assertEquals(responseCode, expectedCode);
        assertEquals(responsePhrase, expectedPhrase);
        assertEquals(reasonHdrCause, expectedReasonCause);
        assertEquals(reasonHdrText, expectedReasonText);

        SipDetails details = response.getSipDetails().orElse(null);
        assertNotNull(details);
        assertEquals(SipDetails.METHOD_PUBLISH, details.getMethod());
        assertEquals(cseq, details.getCSeq());
        assertEquals(responseCode, details.getResponseCode());
        assertEquals(responsePhrase, details.getResponsePhrase());
        assertEquals(reasonHdrCause, details.getReasonHeaderCause());
        assertEquals(reasonHdrText, details.getReasonHeaderText());
        assertEquals(callId, details.getCallId());
    }

    @Test
    @SmallTest
    public void testPublishingStateTargetingEnable() throws Exception {
        doReturn(1).when(mPublishStateCallbacks).getRegisteredCallbackCount();
        Boolean boolObj = Boolean.TRUE;
        Object uid1 = (Object)boolObj;
        doReturn(uid1).when(mPublishStateCallbacks).getRegisteredCallbackCookie(anyInt());

        PublishControllerImpl publishController = createPublishController();

        //To initialize the public state to publish_ok.
        publishController.mCurrentPublishState = RcsUceAdapter.PUBLISH_STATE_OK;

        // send publish request.
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.notifyPendingPublishRequest();

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        int publishState = publishController.mCurrentPublishState;
        int publishStateFromGetUcePublishState = publishController.getUcePublishState(true);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, publishState);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, publishStateFromGetUcePublishState);

        // Set the state to OK as if got a 200 OK response to publish request
        publishController.mCurrentPublishState = RcsUceAdapter.PUBLISH_STATE_OK;

        publishStateFromGetUcePublishState = publishController.getUcePublishState(true);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishStateFromGetUcePublishState);

        // send publish request again.
        callback.notifyPendingPublishRequest();
        handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        publishState = publishController.mCurrentPublishState;
        publishStateFromGetUcePublishState = publishController.getUcePublishState(true);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, publishState);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_PUBLISHING, publishStateFromGetUcePublishState);
    }

    @Test
    @SmallTest
    public void testPublishingStateTargetingDisable() throws Exception {
        doReturn(1).when(mPublishStateCallbacks).getRegisteredCallbackCount();
        Boolean boolObj = Boolean.FALSE;
        Object uid1 = (Object)boolObj;
        doReturn(uid1).when(mPublishStateCallbacks).getRegisteredCallbackCookie(anyInt());

        PublishControllerImpl publishController = createPublishController();
        //To initialize the public state to publish_ok.
        publishController.mCurrentPublishState = RcsUceAdapter.PUBLISH_STATE_OK;
        publishController.mLastPublishState = RcsUceAdapter.PUBLISH_STATE_OK;

        // send publish request
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.notifyPendingPublishRequest();

        //current state is pubilsh_ok. so the state didn`t chaged.
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        int publishState = publishController.mLastPublishState;
        int publishStateFromGetUcePublishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishStateFromGetUcePublishState);

        // set the state to error.
        publishController.mCurrentPublishState = RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
        // send publish request again.
        callback.notifyPendingPublishRequest();
        handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        // the state must be changed to not published.
        publishState = publishController.mLastPublishState;
        publishStateFromGetUcePublishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishStateFromGetUcePublishState);

        // Set the state to OK as if got a 200 OK response to publish request
        publishController.mLastPublishState = RcsUceAdapter.PUBLISH_STATE_OK;
        publishController.mCurrentPublishState = RcsUceAdapter.PUBLISH_STATE_OK;

        publishStateFromGetUcePublishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishStateFromGetUcePublishState);

        // send publish request again.
        callback.notifyPendingPublishRequest();
        handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        // current state is ok so the state didn`t changed.
        publishState = publishController.mLastPublishState;
        publishStateFromGetUcePublishState = publishController.getUcePublishState(false);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishState);
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OK, publishStateFromGetUcePublishState);
    }

    @Test
    @SmallTest
    public void testRequestPublishFromServiceWithoutRcsPresenceCapability() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        // Trigger the PUBLISH request from the service
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        verify(mPublishProcessor, never()).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);

        IImsCapabilityCallback callback = publishController.getRcsCapabilitiesCallback();
        callback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        waitForHandlerAction(handler, 1000);

        verify(mPublishProcessor).checkAndSendPendingRequest();
    }

    @Test
    @SmallTest
    public void testRequestPublishFromServiceWithRcsCapability() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        doReturn(Optional.of(0L)).when(mPublishProcessor).getPublishingDelayTime();

        // Set the PRESENCE is capable
        IImsCapabilityCallback RcsCapCallback = publishController.getRcsCapabilitiesCallback();
        RcsCapCallback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);

        // Trigger the PUBLISH request from the service.
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        verify(mPublishProcessor).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);
    }

    @Test
    @SmallTest
    public void testRequestPublishFromServiceWhenDeviceNoRetry() throws Exception {
        doReturn(true).when(mDeviceStateResult).isPublishRequestBlocked();

        PublishControllerImpl publishController = createPublishController();
        doReturn(Optional.of(0L)).when(mPublishProcessor).getPublishingDelayTime();

        // Set the PRESENCE is capable
        IImsCapabilityCallback RcsCapCallback = publishController.getRcsCapabilitiesCallback();
        RcsCapCallback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);

        // Trigger the PUBLISH request from the service.
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        // Reset device state because isPublishRequestBlocked() is true when Ims Service
        // requests PUBLISH.
        verify(mUceCtrlCallback).resetDeviceState();
        // The PUBLISH request must be pending because the current device state is no_retry.
        verify(mPublishProcessor).setPendingRequest(anyInt());
    }

    @Test
    @SmallTest
    public void testFirstRequestPublishIsTriggeredFromService() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        doReturn(Optional.of(0L)).when(mPublishProcessor).getPublishingDelayTime();

        // Set the PRESENCE is capable
        IImsCapabilityCallback RcsCapCallback = publishController.getRcsCapabilitiesCallback();
        RcsCapCallback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);

        // Trigger a publish request (VT changes)
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.requestPublishFromInternal(PUBLISH_TRIGGER_VT_SETTING_CHANGE);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        // Verify it cannot be processed because the first request should triggred from service.
        verify(mPublishProcessor, never()).doPublish(PUBLISH_TRIGGER_VT_SETTING_CHANGE);

        // Trigger the PUBLISH request from the service.
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);
        waitForHandlerAction(handler, 1000);

        // Verify the request which is from the service can be processed
        verify(mPublishProcessor).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);

        // Trigger the third publish request (VT changes)
        callback.requestPublishFromInternal(PUBLISH_TRIGGER_VT_SETTING_CHANGE);
        waitForHandlerAction(handler, 1000);

        // Verify the publish request can be processed this time.
        verify(mPublishProcessor).doPublish(PublishController.PUBLISH_TRIGGER_VT_SETTING_CHANGE);
    }

    @Test
    @SmallTest
    public void testRequestPublishWhenDeviceCapabilitiesChange() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        doReturn(Optional.of(0L)).when(mPublishProcessor).getPublishingDelayTime();

        // Set the PRESENCE is capable
        IImsCapabilityCallback RcsCapCallback = publishController.getRcsCapabilitiesCallback();
        RcsCapCallback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);

        // Trigger the PUBLISH request from the service.
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        // Verify the request which is from the service can be processed
        verify(mPublishProcessor).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);

        // Trigger the sedond publish (RETRY), it should be processed after 10 seconds.
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.requestPublishFromInternal(PUBLISH_TRIGGER_RETRY);

        // Trigger another publish request (VT changes)
        callback.requestPublishFromInternal(PUBLISH_TRIGGER_VT_SETTING_CHANGE);
        waitForHandlerAction(handler, 1000);

        // Verify the publish request can be processed immediately
        verify(mPublishProcessor).doPublish(PUBLISH_TRIGGER_VT_SETTING_CHANGE);
    }

    @Test
    @SmallTest
    public void testRemoveNumber() {
        // Contrived example, usually formatting of URIs will be consistent in doc.
        final String testString = "<?xml version='1.0' encoding='utf-8' standalone='yes' "
                + "?><presence entity=\"sip:15555551212@example.com\" "
                + "xmlns=\"urn:ietf:params:xml:ns:pidf\" "
                + "xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\" "
                + "xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\"><tuple "
                + "id=\"tid0\"><status><basic>open</basic></status><op:service-description><op"
                + ":service-id>org.3gpp.urn:urn-7:3gpp-application.ims.iari.rcse"
                + ".dp</op:service-id><op:version>1.0</op:version><op:description>Capabilities "
                + "Discovery Service</op:description></op:service-description><contact>sips"
                + ":15555551212@example.com</contact></tuple><tuple "
                + "id=\"tid1\"><status><basic>open</basic></status><op:service-description><op"
                + ":service-id>org.3gpp.urn:urn-7:3gpp-service.ims.icsi"
                + ".mmtel</op:service-id><op:version>1.0</op:version><op:description>Voice and "
                + "Video Service</op:description></op:service-description><caps:servcaps><caps"
                + ":audio>true</caps:audio><caps:video>true</caps:video><caps:duplex><caps"
                + ":supported><caps:full /></caps:supported></caps:duplex></caps:servcaps"
                + "><contact>tel:15555551212@example.com</contact></tuple><tuple "
                + "id=\"tid2\"><status><basic>open</basic></status><op:service-description><op"
                + ":service-id>org.3gpp.urn:urn-7:3gpp-application.ims.iari.rcs"
                + ".geopush</op:service-id><op:version>1"
                + ".0</op:version></op:service-description><contact>sip:1-555-555-1212@example.com"
                + "</contact></tuple><tuple "
                + "id=\"tid3\"><status><basic>open</basic></status><op:service-description><op"
                + ":service-id>org.openmobilealliance:File-Transfer-HTTP</op:service-id><op"
                + ":version>1.0</op:version></op:service-description><contact>tel:1-555-555-1212@"
                + "example.com</contact></tuple><tuple "
                + "id=\"tid4\"><status><basic>open</basic></status><op:service-description><op"
                + ":service-id>org.openmobilealliance:ChatSession</op:service-id><op:version>2"
                + ".0</op:version></op:service-description><contact>sip:+15555551212@example.com"
                + "</contact></tuple></presence>";
        String result = PublishUtils.removeNumbersFromUris(testString);
        // only check for substrings of the full number and variations.
        assertFalse("still contained 5555551212: " + testString, result.contains("5555551212"));
        assertFalse("still contained 555-555-1212: " + testString, result.contains("555-555-1212"));
    }

    @Test
    @SmallTest
    public void testNotPublishWhitSipOptions() throws Exception {
        PublishControllerImpl publishController = createPublishController();
        publishController.setCapabilityType(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
        doReturn(Optional.of(0L)).when(mPublishProcessor).getPublishingDelayTime();

        // Trigger a publish request (VT changes)
        PublishControllerCallback callback = publishController.getPublishControllerCallback();
        callback.requestPublishFromInternal(PUBLISH_TRIGGER_VT_SETTING_CHANGE);
        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);

        // Verify it cannot be processed because the capability type is SIP OPTIONS and the publish
        // request is triggered from device changed
        verify(mPublishProcessor, never()).doPublish(PUBLISH_TRIGGER_VT_SETTING_CHANGE);

        // Set the PRESENCE is capable
        IImsCapabilityCallback RcsCapCallback = publishController.getRcsCapabilitiesCallback();
        RcsCapCallback.onCapabilitiesStatusChanged(RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);

        // Trigger the PUBLISH request from the service.
        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);
        waitForHandlerAction(handler, 1000);

        // Verify the request which is from the service can be processed
        verify(mPublishProcessor).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);
    }

    private PublishControllerImpl createPublishController() {
        PublishControllerImpl publishController = new PublishControllerImpl(mContext, mSubId,
                mUceCtrlCallback, Looper.getMainLooper(), mDeviceCapListenerFactory,
                mPublishProcessorFactory, mUceStatsWriter);
        publishController.setPublishStateCallback(mPublishStateCallbacks);
        publishController.setCapabilityType(RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);
        return publishController;
    }
}
