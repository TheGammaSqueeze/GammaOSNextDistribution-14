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

package com.android.car.audio;

import static android.car.media.CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED;
import static android.car.media.CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED;
import static android.car.media.CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED;
import static android.car.media.CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;

import static org.junit.Assert.assertThrows;

import android.car.CarOccupantZoneManager;
import android.car.VehicleAreaSeat;
import android.car.media.CarAudioManager;
import android.car.media.IMediaAudioRequestStatusCallback;
import android.car.media.IPrimaryZoneMediaAudioRequestCallback;
import android.car.test.AbstractExpectableTestCase;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MediaRequestHandlerTest extends AbstractExpectableTestCase {

    private static final int INVALID_STATUS = 0;
    private static final long TEST_CALLBACK_TIMEOUT_MS = 100;

    private static final int TEST_PASSENGER_OCCUPANT_ZONE_ID = 1;

    private static final CarOccupantZoneManager.OccupantZoneInfo TEST_PASSENGER_OCCUPANT =
            getOccupantInfo(TEST_PASSENGER_OCCUPANT_ZONE_ID,
                    CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER,
                    VehicleAreaSeat.SEAT_ROW_1_RIGHT);

    private MediaRequestHandler mMediaRequestHandler;
    private TestPrimaryZoneMediaAudioRequestCallback mTestZoneAudioRequestCallback;
    private TestMediaRequestStatusCallback mTestMediaRequestStatusCallback;

    @Before
    public void setUp() {
        mMediaRequestHandler = new MediaRequestHandler();
        mTestZoneAudioRequestCallback = new TestPrimaryZoneMediaAudioRequestCallback();
        mTestMediaRequestStatusCallback = new TestMediaRequestStatusCallback();
    }

    @Test
    public void registerPrimaryZoneMediaAudioRequestCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mMediaRequestHandler.registerPrimaryZoneMediaAudioRequestCallback(/* callback= */ null);
        });

        expectWithMessage("Null primary zone media audio request register exception")
                .that(thrown).hasMessageThat().contains("Media request callback");
    }

    @Test
    public void registerPrimaryZoneMediaAudioRequestCallback() {
        boolean registered = mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        expectWithMessage("Primary zone request callback registered status")
                .that(registered).isTrue();
    }

    @Test
    public void unregisterPrimaryZoneMediaAudioRequestCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mMediaRequestHandler.unregisterPrimaryZoneMediaAudioRequestCallback(
                    /* callback= */ null);
        });

        expectWithMessage("Null primary zone media audio request unregister exception")
                .that(thrown).hasMessageThat().contains("Media request callback");
    }

    @Test
    public void unregisterPrimaryZoneMediaAudioRequestCallback() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean unregistered = mMediaRequestHandler
                .unregisterPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        expectWithMessage("Primary zone request callback unregistered status")
                .that(unregistered).isTrue();
    }

    @Test
    public void unregisterPrimaryZoneMediaAudioRequestCallback_withoutRegistering() {
        boolean unregistered = mMediaRequestHandler
                .unregisterPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        expectWithMessage("Unregistered primary zone request callback unregistered status")
                .that(unregistered).isFalse();
    }

    @Test
    public void isAudioMediaCallbackRegistered() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean isRegistered = mMediaRequestHandler
                .isAudioMediaCallbackRegistered(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered status of registered primary zone request callback")
                .that(isRegistered).isTrue();
    }

    @Test
    public void isAudioMediaCallbackRegistered_withNullToken() {
        boolean isRegistered = mMediaRequestHandler.isAudioMediaCallbackRegistered(null);

        expectWithMessage("Registered status of null primary zone request callback")
                .that(isRegistered).isFalse();
    }

    @Test
    public void isAudioMediaCallbackRegistered_withoutRegistering() {
        boolean isRegistered = mMediaRequestHandler
                .isAudioMediaCallbackRegistered(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered status of unregistered primary zone request callback")
                .that(isRegistered).isFalse();
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withNullCallback() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                    /* callback= */ null, TEST_PASSENGER_OCCUPANT);
        });

        expectWithMessage("Null request status callback exception")
                .that(thrown).hasMessageThat().contains("Media audio request status callback");
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withNullOccupantInfo() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mMediaRequestHandler.requestMediaAudioOnPrimaryZone(mTestMediaRequestStatusCallback,
                    /* info= */ null);
        });

        expectWithMessage("Null occupant zone info exception")
                .that(thrown).hasMessageThat().contains("Occupant zone info");
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_reusedCallback() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Re-requested callback request id")
                .that(requestId).isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void requestMediaAudioOnPrimaryZone() {
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Valid request id").that(requestId).isNotEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_callsApprover() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        mTestZoneAudioRequestCallback.waitForCallback();
        expectWithMessage("Received request id")
                .that(mTestZoneAudioRequestCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Received occupant zone info")
                .that(mTestZoneAudioRequestCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
    }

    @Test
    public void requestMediaAudioOnPrimaryZone_withoutApprover() throws Exception {
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        mTestMediaRequestStatusCallback.waitForCallback();
        expectWithMessage("Automatically rejected request id")
                .that(mTestMediaRequestStatusCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Automatically rejected occupant")
                .that(mTestMediaRequestStatusCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Automatically rejected status")
                .that(mTestMediaRequestStatusCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    public void acceptMediaAudioRequest() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean approved = mMediaRequestHandler
                .acceptMediaAudioRequest(mTestZoneAudioRequestCallback, requestId);

        mTestMediaRequestStatusCallback.waitForCallback();
        expectWithMessage("Approved results").that(approved).isTrue();
        expectWithMessage("Approved request id")
                .that(mTestMediaRequestStatusCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Approved occupant")
                .that(mTestMediaRequestStatusCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Approved status")
                .that(mTestMediaRequestStatusCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_APPROVED);
    }

    @Test
    public void acceptMediaAudioRequest_withNullToken() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mMediaRequestHandler
                    .acceptMediaAudioRequest(/* token= */ null, INVALID_REQUEST_ID);
        });

        expectWithMessage("Null approver for acceptance").that(thrown).hasMessageThat()
                .contains("Media request token");
    }

    @Test
    public void acceptMediaAudioRequest_withInvalidId() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean approved = mMediaRequestHandler
                .acceptMediaAudioRequest(mTestZoneAudioRequestCallback, INVALID_REQUEST_ID);

        expectWithMessage("Approved with invalid id results").that(approved).isFalse();
    }

    @Test
    public void rejectMediaAudioRequest() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean rejected = mMediaRequestHandler.rejectMediaAudioRequest(requestId);

        mTestMediaRequestStatusCallback.waitForCallback();
        expectWithMessage("Rejected results").that(rejected).isTrue();
        expectWithMessage("Rejected request id")
                .that(mTestMediaRequestStatusCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Rejected occupant")
                .that(mTestMediaRequestStatusCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Rejected status").that(mTestMediaRequestStatusCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    public void rejectMediaAudioRequest_withInvalidRequest() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean rejected = mMediaRequestHandler.rejectMediaAudioRequest(INVALID_REQUEST_ID);

        expectWithMessage("Rejected with invalid id results").that(rejected).isFalse();
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean Canceled = mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);

        mTestMediaRequestStatusCallback.waitForCallback();
        expectWithMessage("Canceled results").that(Canceled).isTrue();
        expectWithMessage("Canceled request id")
                .that(mTestMediaRequestStatusCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Canceled occupant")
                .that(mTestMediaRequestStatusCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Canceled status").that(mTestMediaRequestStatusCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone_callsApprover() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean Canceled = mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);

        mTestZoneAudioRequestCallback.waitForCallback();
        expectWithMessage("Received Canceled results").that(Canceled).isTrue();
        expectWithMessage("Received Canceled request id")
                .that(mTestZoneAudioRequestCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Received Canceled occupant")
                .that(mTestZoneAudioRequestCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Received Canceled status").that(mTestZoneAudioRequestCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    public void cancelMediaAudioOnPrimaryZone_withInvalidId() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean rejected = mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(INVALID_REQUEST_ID);

        expectWithMessage("Canceled with invalid id results").that(rejected).isFalse();
    }

    @Test
    public void stopMediaAudioOnPrimaryZone() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean stopped = mMediaRequestHandler.stopMediaAudioOnPrimaryZone(requestId);

        mTestMediaRequestStatusCallback.waitForCallback();
        expectWithMessage("Stopped results").that(stopped).isTrue();
        expectWithMessage("Stopped request id")
                .that(mTestMediaRequestStatusCallback.mRequestId).isEqualTo(requestId);
        expectWithMessage("Stopped occupant")
                .that(mTestMediaRequestStatusCallback.mInfo).isEqualTo(TEST_PASSENGER_OCCUPANT);
        expectWithMessage("Stopped status").that(mTestMediaRequestStatusCallback.mStatus)
                .isEqualTo(AUDIO_REQUEST_STATUS_STOPPED);
    }

    @Test
    public void stopMediaAudioOnPrimaryZone_withInvalidId() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        boolean stopped = mMediaRequestHandler.stopMediaAudioOnPrimaryZone(INVALID_REQUEST_ID);

        expectWithMessage("Stopped with invalid id results").that(stopped).isFalse();
    }

    @Test
    public void getOccupantForRequest_withoutApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        expectWithMessage("Occupant for request %s", requestId)
                .that(info).isEqualTo(TEST_PASSENGER_OCCUPANT);
    }

    @Test
    public void getOccupantForRequest_afterApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback, requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        expectWithMessage("Approved occupant for request %s", requestId)
                .that(info).isEqualTo(TEST_PASSENGER_OCCUPANT);
    }

    @Test
    public void getOccupantForRequest_afterRejection() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.rejectMediaAudioRequest(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        expectWithMessage("Rejected occupant for request %s", requestId).that(info).isNull();
    }

    @Test
    public void getOccupantForRequest_afterCanceled() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        expectWithMessage("Canceled occupant for request %s", requestId).that(info).isNull();
    }

    @Test
    public void getOccupantForRequest_withInvalidRequest() throws Exception {
        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(INVALID_REQUEST_ID);

        expectWithMessage("Occupant for invalid request id").that(info).isNull();
    }

    @Test
    public void getRequestIdForOccupant_withoutRequest() throws Exception {
        long requestId = mMediaRequestHandler.getRequestIdForOccupant(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Request id for unset occupant")
                .that(requestId).isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void getRequestIdForOccupant_withoutApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long unapprovedRequestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        long requestId = mMediaRequestHandler.getRequestIdForOccupant(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Unapproved request id").that(requestId).isEqualTo(unapprovedRequestId);
    }

    @Test
    public void getRequestIdForOccupant_afterApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long approvedRequestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback,
                approvedRequestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        long requestId = mMediaRequestHandler.getRequestIdForOccupant(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Approved request id").that(requestId).isEqualTo(approvedRequestId);
    }

    @Test
    public void getAssignedRequestIdForOccupantZoneId_withoutRequest() throws Exception {
        long requestId = mMediaRequestHandler.getAssignedRequestIdForOccupantZoneId(
                TEST_PASSENGER_OCCUPANT_ZONE_ID);

        expectWithMessage("Request id for unset occupant zone id")
                .that(requestId).isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void getAssignedRequestIdForOccupantZoneId_withoutApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        long requestId = mMediaRequestHandler.getAssignedRequestIdForOccupantZoneId(
                TEST_PASSENGER_OCCUPANT_ZONE_ID);

        expectWithMessage("Unapproved request id for zone id").that(requestId)
                .isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void getAssignedRequestIdForOccupantZoneId_afterApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long approvedRequestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback,
                approvedRequestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        long requestId = mMediaRequestHandler.getAssignedRequestIdForOccupantZoneId(
                TEST_PASSENGER_OCCUPANT_ZONE_ID);

        expectWithMessage("Approved request id for zone id").that(requestId)
                .isEqualTo(approvedRequestId);
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_withoutRequest() {
        boolean allowed = mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Unset occupant allowed status").that(allowed).isFalse();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_withoutApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);

        boolean allowed = mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Unapproved occupant allowed status").that(allowed).isFalse();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_afterApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long approvedRequestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback,
                approvedRequestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        boolean allowed = mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Approved occupant allowed status").that(allowed).isTrue();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_afterRejection() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.rejectMediaAudioRequest(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        boolean allowed = mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Rejected occupant allowed status").that(allowed).isFalse();
    }

    @Test
    public void isMediaAudioAllowedInPrimaryZone_afterApprovalThenCanceled() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback, requestId);
        mTestMediaRequestStatusCallback.waitForCallback();
        mTestZoneAudioRequestCallback.reset();
        mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        boolean allowed = mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(TEST_PASSENGER_OCCUPANT);

        expectWithMessage("Toggled occupant allowed status").that(allowed).isFalse();
    }

    @Test
    public void getRequestsOwnedByApprover_notYetRegistered() {
        List<Long> requests = mMediaRequestHandler
                .getRequestsOwnedByApprover(mTestZoneAudioRequestCallback);

        expectWithMessage("Unregistered token requests").that(requests).isEmpty();
    }

    @Test
    public void getRequestsOwnedByApprover_afterRegistering() {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);

        List<Long> requests = mMediaRequestHandler
                .getRequestsOwnedByApprover(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered token requests").that(requests).isEmpty();
    }

    @Test
    public void getRequestsOwnedByApprover_afterApproval() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long approvedRequestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback,
                approvedRequestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        List<Long> requests = mMediaRequestHandler
                .getRequestsOwnedByApprover(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered token approved requests")
                .that(requests).contains(approvedRequestId);
    }

    @Test
    public void getRequestsOwnedByApprover_afterRejection() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.rejectMediaAudioRequest(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        List<Long> requests = mMediaRequestHandler
                .getRequestsOwnedByApprover(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered token requests after rejection").that(requests).isEmpty();
    }

    @Test
    public void getRequestsOwnedByApprover_afterApprovalThenCancel() throws Exception {
        mMediaRequestHandler
                .registerPrimaryZoneMediaAudioRequestCallback(mTestZoneAudioRequestCallback);
        long requestId = mMediaRequestHandler.requestMediaAudioOnPrimaryZone(
                mTestMediaRequestStatusCallback, TEST_PASSENGER_OCCUPANT);
        mMediaRequestHandler.acceptMediaAudioRequest(mTestZoneAudioRequestCallback, requestId);
        mTestMediaRequestStatusCallback.waitForCallback();
        mTestZoneAudioRequestCallback.reset();
        mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);
        mTestMediaRequestStatusCallback.waitForCallback();

        List<Long> requests = mMediaRequestHandler
                .getRequestsOwnedByApprover(mTestZoneAudioRequestCallback);

        expectWithMessage("Registered token requests after cancel").that(requests).isEmpty();
    }

    private static final class TestPrimaryZoneMediaAudioRequestCallback extends
            IPrimaryZoneMediaAudioRequestCallback.Stub {
        private long mRequestId = INVALID_REQUEST_ID;
        private CarOccupantZoneManager.OccupantZoneInfo mInfo;
        private CountDownLatch mStatusLatch = new CountDownLatch(1);
        private int mStatus;

        @Override
        public void onRequestMediaOnPrimaryZone(CarOccupantZoneManager.OccupantZoneInfo info,
                long requestId) {
            mInfo = info;
            mRequestId = requestId;
            mStatusLatch.countDown();
        }

        @Override
        public void onMediaAudioRequestStatusChanged(
                CarOccupantZoneManager.OccupantZoneInfo info,
                long requestId, int status) {
            mInfo = info;
            mRequestId = requestId;
            mStatus = status;
            mStatusLatch.countDown();
        }

        private void waitForCallback() throws Exception {
            mStatusLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public void reset() {
            mInfo = null;
            mRequestId = INVALID_REQUEST_ID;
            mStatus = INVALID_STATUS;
            mStatusLatch = new CountDownLatch(1);
        }
    }

    private static final class TestMediaRequestStatusCallback extends
            IMediaAudioRequestStatusCallback.Stub {
        private long mRequestId = INVALID_REQUEST_ID;
        private CarOccupantZoneManager.OccupantZoneInfo mInfo;
        private int mStatus;
        private CountDownLatch mStatusLatch = new CountDownLatch(1);

        @Override
        public void onMediaAudioRequestStatusChanged(
                CarOccupantZoneManager.OccupantZoneInfo info,
                long requestId, @CarAudioManager.MediaAudioRequestStatus int status)
                throws RemoteException {
            mInfo = info;
            mRequestId = requestId;
            mStatus = status;
            mStatusLatch.countDown();
        }

        private void waitForCallback() throws Exception {
            mStatusLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static CarOccupantZoneManager.OccupantZoneInfo getOccupantInfo(int occupantZoneId,
            int occupantType, int seat) {
        return new CarOccupantZoneManager.OccupantZoneInfo(occupantZoneId, occupantType, seat);
    }
}
