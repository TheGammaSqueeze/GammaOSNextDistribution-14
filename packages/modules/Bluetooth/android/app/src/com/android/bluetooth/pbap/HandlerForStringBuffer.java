/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.pbap;

import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.util.Log;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.obex.Operation;

import java.io.IOException;
import java.io.OutputStream;

/** Handler to emit vCards to PCE. */
// Next tag value for ContentProfileErrorReportUtils.report(): 2
public class HandlerForStringBuffer {
    private static final String TAG = "HandlerForStringBuffer";

    private final Operation mOperation;
    private final String mOwnerVCard;

    private OutputStream mOutputStream;

    public HandlerForStringBuffer(Operation op, String ownerVCard) {
        mOperation = op;
        mOwnerVCard = ownerVCard;
        if (BluetoothPbapService.VERBOSE) {
            Log.v(TAG, "ownerVCard \n " + mOwnerVCard);
        }
    }

    public boolean init() {
        try {
            mOutputStream = mOperation.openOutputStream();
            if (mOwnerVCard != null) {
                return writeVCard(mOwnerVCard);
            }
            return true;
        } catch (IOException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_HANDLER_FOR_STRING_BUFFER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    0);
            Log.e(TAG, "openOutputStream failed", e);
        }
        return false;
    }

    public boolean writeVCard(String vCard) {
        try {
            if (vCard != null) {
                mOutputStream.write(vCard.getBytes());
                return true;
            }
        } catch (IOException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_HANDLER_FOR_STRING_BUFFER,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    1);
            Log.e(TAG, "write failed", e);
        }
        return false;
    }

    public void terminate() {
        boolean result = BluetoothPbapObexServer.closeStream(mOutputStream, mOperation);
        if (BluetoothPbapService.VERBOSE) {
            if (result) {
                Log.v(TAG, "closeStream succeeded!");
            } else {
                Log.v(TAG, "closeStream failed!");
            }
        }
    }
}
