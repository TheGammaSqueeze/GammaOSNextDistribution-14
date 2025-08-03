/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.bluetooth.flags.Flags;

/**
 * Receives and handles: system broadcasts; Intents from other applications; Intents from
 * OppService; Intents from modules in Opp application layer.
 */
// Next tag value for ContentProfileErrorReportUtils.report(): 2
public class BluetoothOppReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothOppReceiver";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (D) Log.d(TAG, " action :" + action);
        if (action == null) return;
        if (action.equals(BluetoothDevicePicker.ACTION_DEVICE_SELECTED)) {
            BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(context);

            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (remoteDevice == null) {
                mOppManager.cleanUpSendingFileInfo();
                return;
            }

            if (D) {
                Log.d(
                        TAG,
                        "Received BT device selected intent, bt device: "
                                + remoteDevice.getIdentityAddress());
            }

            // Insert transfer session record to database
            mOppManager.startTransfer(remoteDevice);

            // Display toast message
            String deviceName = mOppManager.getDeviceName(remoteDevice);
            String toastMsg;
            int batchSize = mOppManager.getBatchSize();
            if (mOppManager.mMultipleFlag) {
                toastMsg = context.getString(R.string.bt_toast_5, Integer.toString(batchSize),
                        deviceName);
            } else {
                toastMsg = context.getString(R.string.bt_toast_4, deviceName);
            }
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
        } else if (action.equals(Constants.ACTION_INCOMING_FILE_CONFIRM)
                && !Flags.oppStartActivityDirectlyFromNotification()) {
            if (V) {
                Log.v(TAG, "Receiver ACTION_INCOMING_FILE_CONFIRM");
            }

            Uri uri = intent.getData();
            Intent in = new Intent(context, BluetoothOppIncomingFileConfirmActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.setDataAndNormalize(uri);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_DECLINE)) {
            if (V) {
                Log.v(TAG, "Receiver ACTION_DECLINE");
            }

            Uri uri = intent.getData();
            ContentValues values = new ContentValues();
            values.put(BluetoothShare.USER_CONFIRMATION, BluetoothShare.USER_CONFIRMATION_DENIED);
            BluetoothMethodProxy.getInstance().contentResolverUpdate(context.getContentResolver(),
                    uri, values, null, null);
            cancelNotification(context, BluetoothOppNotification.NOTIFICATION_ID_PROGRESS);

        } else if (action.equals(Constants.ACTION_ACCEPT)) {
            if (V) {
                Log.v(TAG, "Receiver ACTION_ACCEPT");
            }

            Uri uri = intent.getData();
            ContentValues values = new ContentValues();
            values.put(BluetoothShare.USER_CONFIRMATION,
                    BluetoothShare.USER_CONFIRMATION_CONFIRMED);
            BluetoothMethodProxy.getInstance().contentResolverUpdate(context.getContentResolver(),
                    uri, values, null, null);
        } else if (action.equals(Constants.ACTION_OPEN) || action.equals(Constants.ACTION_LIST)) {
            if (V) {
                if (action.equals(Constants.ACTION_OPEN)) {
                    Log.v(TAG, "Receiver open for " + intent.getData());
                } else {
                    Log.v(TAG, "Receiver list for " + intent.getData());
                }
            }

            BluetoothOppTransferInfo transInfo = new BluetoothOppTransferInfo();
            Uri uri = intent.getData();
            transInfo = BluetoothOppUtility.queryRecord(context, uri);
            if (transInfo == null) {
                Log.e(TAG, "Error: Can not get data from db");
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_RECEIVER,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        0);
                return;
            }

            if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND
                    && BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                // if received file successfully, open this file
                BluetoothOppUtility.openReceivedFile(context, transInfo.mFileName,
                        transInfo.mFileType, transInfo.mTimeStamp, uri);
                BluetoothOppUtility.updateVisibilityToHidden(context, uri);
            } else {
                Intent in = new Intent(context, BluetoothOppTransferActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                in.setDataAndNormalize(uri);
                context.startActivity(in);
            }

        } else if (action.equals(Constants.ACTION_OPEN_OUTBOUND_TRANSFER)
                && !Flags.oppStartActivityDirectlyFromNotification()) {
            if (V) {
                Log.v(TAG, "Received ACTION_OPEN_OUTBOUND_TRANSFER.");
            }

            Intent in = new Intent(context, BluetoothOppTransferHistory.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            in.putExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_OUTBOUND);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_OPEN_INBOUND_TRANSFER)
                && !Flags.oppStartActivityDirectlyFromNotification()) {
            if (V) {
                Log.v(TAG, "Received ACTION_OPEN_INBOUND_TRANSFER.");
            }

            Intent in = new Intent(context, BluetoothOppTransferHistory.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            in.putExtra(Constants.EXTRA_DIRECTION, BluetoothShare.DIRECTION_INBOUND);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_HIDE)) {
            if (V) {
                Log.v(TAG, "Receiver hide for " + intent.getData());
            }
            Cursor cursor = BluetoothMethodProxy.getInstance().contentResolverQuery(
                    context.getContentResolver(), intent.getData(), null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int visibilityColumn = cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY);
                    int visibility = cursor.getInt(visibilityColumn);
                    int userConfirmationColumn =
                            cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION);
                    int userConfirmation = cursor.getInt(userConfirmationColumn);
                    if (((userConfirmation == BluetoothShare.USER_CONFIRMATION_PENDING))
                            && visibility == BluetoothShare.VISIBILITY_VISIBLE) {
                        ContentValues values = new ContentValues();
                        values.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
                        BluetoothMethodProxy.getInstance().contentResolverUpdate(
                                context.getContentResolver(), intent.getData(), values, null,
                                null);
                        if (V) {
                            Log.v(TAG, "Action_hide received and db updated");
                        }
                    }
                }
                cursor.close();
            }
        } else if (action.equals(Constants.ACTION_COMPLETE_HIDE)
                && !Flags.oppFixMultipleNotificationsIssues()) {
            if (V) {
                Log.v(TAG, "Receiver ACTION_COMPLETE_HIDE");
            }
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
            BluetoothMethodProxy.getInstance().contentResolverUpdate(
                    context.getContentResolver(), BluetoothShare.CONTENT_URI, updateValues,
                    BluetoothOppNotification.WHERE_COMPLETED, null);
        } else if (action.equals(Constants.ACTION_HIDE_COMPLETED_INBOUND_TRANSFER)
                && Flags.oppFixMultipleNotificationsIssues()) {
            if (V) {
                Log.v(TAG, "Received ACTION_HIDE_COMPLETED_INBOUND_TRANSFER");
            }
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
            BluetoothMethodProxy.getInstance()
                    .contentResolverUpdate(
                            context.getContentResolver(),
                            BluetoothShare.CONTENT_URI,
                            updateValues,
                            BluetoothOppNotification.WHERE_COMPLETED_INBOUND,
                            null);
        } else if (action.equals(Constants.ACTION_HIDE_COMPLETED_OUTBOUND_TRANSFER)
                && Flags.oppFixMultipleNotificationsIssues()) {
            if (V) {
                Log.v(TAG, "Received ACTION_HIDE_COMPLETED_OUTBOUND_TRANSFER");
            }
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
            BluetoothMethodProxy.getInstance()
                    .contentResolverUpdate(
                            context.getContentResolver(),
                            BluetoothShare.CONTENT_URI,
                            updateValues,
                            BluetoothOppNotification.WHERE_COMPLETED_OUTBOUND,
                            null);
        } else if (action.equals(BluetoothShare.TRANSFER_COMPLETED_ACTION)) {
            if (V) {
                Log.v(TAG, "Receiver Transfer Complete Intent for " + intent.getData());
            }

            String toastMsg = null;
            BluetoothOppTransferInfo transInfo = new BluetoothOppTransferInfo();
            transInfo = BluetoothOppUtility.queryRecord(context, intent.getData());
            if (transInfo == null) {
                Log.e(TAG, "Error: Can not get data from db");
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_RECEIVER,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        1);
                return;
            }

            if (transInfo.mHandoverInitiated) {
                // Deal with handover-initiated transfers separately
                Intent handoverIntent = new Intent(Constants.ACTION_BT_OPP_TRANSFER_DONE);
                if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION,
                            Constants.DIRECTION_BLUETOOTH_INCOMING);
                } else {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION,
                            Constants.DIRECTION_BLUETOOTH_OUTGOING);
                }
                handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, transInfo.mID);
                handoverIntent.putExtra(Constants.EXTRA_BT_OPP_ADDRESS, transInfo.mDestAddr);

                if (BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_STATUS,
                            Constants.HANDOVER_TRANSFER_STATUS_SUCCESS);
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_URI,
                            transInfo.mFileName);
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_MIMETYPE,
                            transInfo.mFileType);
                } else {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_STATUS,
                            Constants.HANDOVER_TRANSFER_STATUS_FAILURE);
                }
                Utils.sendBroadcast(context, handoverIntent, Constants.HANDOVER_STATUS_PERMISSION,
                        Utils.getTempAllowlistBroadcastOptions());
                return;
            }

            if (BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                if (transInfo.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    toastMsg = context.getString(R.string.notification_sent, transInfo.mFileName);
                } else if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    toastMsg =
                            context.getString(R.string.notification_received, transInfo.mFileName);
                }

            } else if (BluetoothShare.isStatusError(transInfo.mStatus)) {
                if (transInfo.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    toastMsg =
                            context.getString(R.string.notification_sent_fail, transInfo.mFileName);
                } else if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    toastMsg = context.getString(R.string.download_fail_line1);
                }
            }
            if (V) {
                Log.v(TAG, "Toast msg == " + toastMsg);
            }
            if (toastMsg != null) {
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void cancelNotification(Context context, int id) {
        NotificationManager notMgr = context.getSystemService(NotificationManager.class);
        if (notMgr == null) {
            return;
        }
        notMgr.cancel(id);
        if (V) {
            Log.v(TAG, "notMgr.cancel called");
        }
    }
}
