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

package com.google.android.chre.test.chqts;

import android.hardware.location.ContextHubClient;
import android.hardware.location.NanoAppBinary;

import androidx.annotation.NonNull;

import com.google.android.utils.chre.ChreApiTestUtil;

import org.junit.Assert;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.chre.rpc.proto.ChreApiTest;

/**
 * Test to ensure CHRE HostEndpoint related API works as expected.
 */
public class ContextHubHostEndpointInfoTestExecutor extends ContextHubChreApiTestExecutor {
    private static final int HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT = 0;
    private static final int CHRE_HOST_ENDPOINT_NOTIFICATION_EVENT = 0x0008;

    public ContextHubHostEndpointInfoTestExecutor(NanoAppBinary nanoapp) {
        super(nanoapp);
    }

    /**
     * Validates if the host endpoint info stored in ChreApiTest Nanoapp is as
     * expected.
     *
     * @param id      the host endpoint id of the host endpoint.
     * @param success true if we are expecting to retrieve host endpoint info by
     *                this id, otherwise false.
     */
    private void validateHostEndpointInfoById(int id, boolean success) throws Exception {
        ChreApiTest.ChreGetHostEndpointInfoInput input =
                ChreApiTest.ChreGetHostEndpointInfoInput.newBuilder()
                .setHostEndpointId(id)
                .build();
        ChreApiTest.ChreGetHostEndpointInfoOutput response =
                ChreApiTestUtil.callUnaryRpcMethodSync(
                        getRpcClient(), "chre.rpc.ChreApiTestService.ChreGetHostEndpointInfo",
                        input);
        if (!success) {
            Assert.assertFalse(
                    "Received host endpoint info for not connected id", response.getStatus());
        } else {
            Assert.assertTrue("Did not receive host endpoint info", response.getStatus());
            Assert.assertEquals("Host endpoint Id mismatch", response.getHostEndpointId(), id);
            Assert.assertTrue("IsNameValid should be true", response.getIsNameValid());
            Assert.assertFalse("IsTagValid should be false", response.getIsTagValid());
            Assert.assertEquals("Host endpoint name mismatch", mContext.getPackageName(),
                    response.getEndpointName());
        }
    }

    /**
     * Test if we can get host endpoint info by id for connected host endpoint, and if we get
     * failure result to query unconnected host endpoint info.
     */
    public void testGetHostEndpointInfo() throws Exception {
        ContextHubClient firstClient = mContextHubManager.createClient(mContextHub, this);
        ContextHubClient secondClient = mContextHubManager.createClient(mContextHub, this);
        int firstClientHubId = firstClient.getId();
        int secondClientHubId = secondClient.getId();
        validateHostEndpointInfoById(firstClientHubId, true);
        validateHostEndpointInfoById(secondClientHubId, true);

        secondClient.close();

        validateHostEndpointInfoById(firstClientHubId, true);
        validateHostEndpointInfoById(secondClientHubId, false);

        firstClient.close();
    }

    /**
     * Validates if the nanoapp received a proper host endpoint notification disconnection event.
     *
     * @param id host endpoint id for the most recent disconnected host endpoint.
     * @param events host endpoint notification events received by the nanoapp.
     */
    private void validateChreHostEndpointNotification(int id,
            @NonNull List<ChreApiTest.GeneralEventsMessage> events) throws Exception {
        Objects.requireNonNull(events);
        Assert.assertEquals(
                "Should have exactly receive 1 host endpoint notification",
                1, events.size());
        ChreApiTest.ChreHostEndpointNotification hostEndpointNotification =
                events.get(0).getChreHostEndpointNotification();
        Assert.assertTrue(events.get(0).getStatus());
        Assert.assertEquals("Host endpoint Id mismatch", id,
                hostEndpointNotification.getHostEndpointId());

        Assert.assertEquals("Not receive HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT",
                HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT,
                hostEndpointNotification.getNotificationType());
    }

    /**
     * Asks the test nanoapp to configure host endpoint notification.
     *
     * @param hostEndpointId which host endpoint to get notification.
     * @param enable         true to enable host endpoint notification, false to
     *                       disable.
     */
    private void configureHostEndpointNotification(int hostEndpointId, boolean enable)
            throws Exception {
        ChreApiTest.ChreConfigureHostEndpointNotificationsInput request =
                ChreApiTest.ChreConfigureHostEndpointNotificationsInput.newBuilder()
                        .setHostEndpointId(hostEndpointId)
                        .setEnable(enable)
                        .build();
        ChreApiTest.Status response =
                ChreApiTestUtil.callUnaryRpcMethodSync(
                        getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreConfigureHostEndpointNotifications",
                        request);
        Assert.assertTrue("Failed to configure host endpoint notification", response.getStatus());
    }

    /**
     * Test if we can register host endpoint notification and if it receives the event once a
     * registered host endpoint got disconnected.
     */
    public void testGetHostEndpointNotificationOnDisconnect() throws Exception {
        ContextHubClient client = mContextHubManager.createClient(mContextHub, this);
        int clientHostEndpointId = client.getId();
        configureHostEndpointNotification(clientHostEndpointId, true);
        Future<List<ChreApiTest.GeneralEventsMessage>> eventsFuture = new ChreApiTestUtil()
                .gatherEvents(mRpcClients.get(0),
                        CHRE_HOST_ENDPOINT_NOTIFICATION_EVENT,
                        /* eventCount= */ 1,
                        ChreApiTestUtil.RPC_TIMEOUT_IN_NS);
        client.close();
        List<ChreApiTest.GeneralEventsMessage> events =
                        eventsFuture.get(2 * ChreApiTestUtil.RPC_TIMEOUT_IN_NS,
                                TimeUnit.NANOSECONDS);
        validateChreHostEndpointNotification(clientHostEndpointId, events);
    }
}
