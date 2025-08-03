/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.pbap;

import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.util.Log;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.obex.Authenticator;
import com.android.obex.PasswordAuthentication;

/**
 * BluetoothPbapAuthenticator is a used by BluetoothObexServer for obex authentication procedure.
 */
// Next tag value for ContentProfileErrorReportUtils.report(): 1
public class BluetoothPbapAuthenticator implements Authenticator {
    private static final String TAG = "PbapAuthenticator";

    @VisibleForTesting boolean mChallenged;
    @VisibleForTesting boolean mAuthCancelled;
    @VisibleForTesting String mSessionKey;
    @VisibleForTesting PbapStateMachine mPbapStateMachine;

    BluetoothPbapAuthenticator(final PbapStateMachine stateMachine) {
        mPbapStateMachine = stateMachine;
        mChallenged = false;
        mAuthCancelled = false;
        mSessionKey = null;
    }

    final synchronized void setChallenged(final boolean bool) {
        mChallenged = bool;
        notify();
    }

    final synchronized void setCancelled(final boolean bool) {
        mAuthCancelled = bool;
        notify();
    }

    final synchronized void setSessionKey(final String string) {
        mSessionKey = string;
    }

    private void waitUserConfirmation() {
        mPbapStateMachine.sendMessage(PbapStateMachine.CREATE_NOTIFICATION);
        mPbapStateMachine.sendMessageDelayed(PbapStateMachine.REMOVE_NOTIFICATION,
                BluetoothPbapService.USER_CONFIRM_TIMEOUT_VALUE);
        synchronized (this) {
            while (!mChallenged && !mAuthCancelled) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_AUTHENTICATOR,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                            0);
                    Log.e(TAG, "Interrupted while waiting on isChallenged or AuthCancelled");
                }
            }
        }
    }

    @Override
    public PasswordAuthentication onAuthenticationChallenge(final String description,
            final boolean isUserIdRequired, final boolean isFullAccess) {
        waitUserConfirmation();
        if (mSessionKey.trim().length() != 0) {
            return new PasswordAuthentication(null, mSessionKey.getBytes());
        }
        return null;
    }

    // TODO: Reserved for future use only, in case PSE challenge PCE
    @Override
    public byte[] onAuthenticationResponse(final byte[] userName) {
        byte[] b = null;
        return b;
    }
}
