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

package com.google.android.car.multidisplaytest.occupantconnection;

import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_USER_REJECTED;

import static com.google.android.car.multidisplaytest.occupantconnection.Constants.ACCEPTED_RESULT_CODE;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.ACTION_CONNECTION_CANCELLED;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.KEY_MESSAGE_RECEIVER;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.REJECTED_RESULT_CODE;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.SEEK_BAR_RECEIVER_ID;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.SYSTEM_PROPERTY_KEY_CONNECTION_NEEDS_USER_APPROVAL;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.TEXT_RECEIVER_ID;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.occupantconnection.AbstractReceiverService;
import android.car.occupantconnection.Payload;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemProperties;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.google.android.car.multidisplaytest.occupantconnection.PayloadProto.Data;

/**
 * {@inheritDoc}
 *
 * Run `adb shell setprop multidisplaytest.occupantconnection.need_approval false` to disable user
 * approval, and `adb shell setprop multidisplaytest.occupantconnection.need_approval true` to
 * enable it. By default, it is enabled. When it is enabled, the connection request must be approved
 * by the user explicitly. Otherwise, it will be accepted automatically without user interaction.
 */
public class ReceiverService extends AbstractReceiverService {

    // The member variables are accessed by the main thread only, so there is no multi-thread issue.
    private String mTag;
    private OccupantZoneInfo mCachedSenderZone;
    private Payload mCachedPayload;

    /** Used by PermissionActivity to send the user confirmation result back to this service. */
    class MessageReceiver extends ResultReceiver {

        private final OccupantZoneInfo mSenderZone;

        MessageReceiver(OccupantZoneInfo senderZone) {
            super(/* handler= */ null);
            mSenderZone = senderZone;
        }

        /** Called when there's a result available. */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
                case ACCEPTED_RESULT_CODE:
                    Slog.d(mTag, "Accept connection from sender " + mSenderZone
                            + " because the user has approved it");
                    acceptConnection(mSenderZone);
                    break;
                case REJECTED_RESULT_CODE:
                    Slog.d(mTag, "Reject connection from sender " + mSenderZone
                            + " because the user has rejected it");
                    rejectConnection(mSenderZone, CONNECTION_ERROR_USER_REJECTED);
                    break;
                default:
                    throw new IllegalStateException(mTag + " Unknown resultCode: " + resultCode);
            }
        }
    }

    @Override
    public void onCreate() {
        mTag = "OccupantConnection##" + getUserId();
        super.onCreate();
    }

    /**
     * If the receiver endpoint has been registered, forward the Payload immediately after
     * receiving it. Otherwise, abandon the Payload for TextReceiverFragment, but cache the Payload
     * for SeekBarReceiverFragment.
     */
    @Override
    public void onPayloadReceived(@NonNull OccupantZoneInfo senderZone,
            @NonNull Payload payload) {
        Data data = PayloadUtils.parseData(payload);
        String receiverEndpointId = data.getReceiverEndpointId();
        Slog.d(mTag, "onPayloadReceived:senderZone=" + senderZone
                + ", receiverEndpointId=" + receiverEndpointId);
        if (TEXT_RECEIVER_ID.equals(receiverEndpointId)) {
            boolean success = forwardPayload(senderZone, receiverEndpointId, payload);
            if (success) {
                Slog.d(mTag, "Payload has been forwarded to " + receiverEndpointId);
            } else {
                Slog.d(mTag, "Abandon the payload because " + receiverEndpointId
                        + " is not registered yet");
            }
            return;
        }
        if (SEEK_BAR_RECEIVER_ID.equals(receiverEndpointId)) {
            boolean success = forwardPayload(senderZone, receiverEndpointId, payload);
            if (success) {
                Slog.d(mTag, "Payload has been forwarded to " + receiverEndpointId);
            } else {
                Slog.d(mTag, "Cache the payload because " + receiverEndpointId
                        + " is not registered yet");
                mCachedSenderZone = senderZone;
                mCachedPayload = payload;
            }
            return;
        }
        throw new IllegalStateException("Unknown receiverEndpointId: " + receiverEndpointId);
    }

    @Override
    public void onReceiverRegistered(@NonNull String receiverEndpointId) {
        Slog.d(mTag, "onReceiverRegistered(" + receiverEndpointId + ")");
        if (SEEK_BAR_RECEIVER_ID.equals(receiverEndpointId) && mCachedSenderZone != null
                && mCachedPayload != null) {
            forwardPayload(mCachedSenderZone, SEEK_BAR_RECEIVER_ID, mCachedPayload);
        }
    }

    @Override
    public void onConnectionInitiated(@NonNull OccupantZoneInfo senderZone) {
        boolean needsUserApproval = SystemProperties.getBoolean(
                SYSTEM_PROPERTY_KEY_CONNECTION_NEEDS_USER_APPROVAL, /* def= */ true);
        Slog.d(mTag, "onConnectionInitiated:senderZone=" + senderZone
                + ", needsUserApproval=" + needsUserApproval);
        if (needsUserApproval) {
            Intent intent = new Intent(this, PermissionActivity.class);
            // Pack the parcelable receiver into the intent extras so PermissionActivity can
            // access it.
            intent.putExtra(KEY_MESSAGE_RECEIVER, new MessageReceiver(senderZone));
            // Calling startActivity() from outside an Activity context requires the
            // FLAG_ACTIVITY_NEW_TASK flag.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Note: startActivityForResult() doesn't work from a service. That's why we have to
            // use the MessageReceiver to get the activity result.
            startActivity(intent);
        } else {
            Slog.d(mTag, "Accept connection from sender " + senderZone + " automatically");
            acceptConnection(senderZone);
        }
    }

    @Override
    public void onConnected(@NonNull OccupantZoneInfo senderZone) {
        Slog.d(mTag, "onConnected(" + senderZone + ")");
    }

    @Override
    public void onConnectionCanceled(@NonNull OccupantZoneInfo senderZone) {
        Slog.d(mTag, "onConnectionCanceled(" + senderZone + ")");
        sendBroadcast(new Intent(ACTION_CONNECTION_CANCELLED));
    }

    @Override
    public void onDisconnected(@NonNull OccupantZoneInfo senderZone) {
        Slog.d(mTag, "onDisconnected(" + senderZone + ")");
    }

    @Override
    public void onDestroy() {
        Slog.d(mTag, "onDestroy()");
        super.onDestroy();
    }
}
