/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cellbroadcastservice;

import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERR_CDMA_DECODING;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.RPT_CDMA;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.SRC_CBS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.CellBroadcastService;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The default implementation of CellBroadcastService, which is used for handling GSM and CDMA cell
 * broadcast messages.
 */
public class DefaultCellBroadcastService extends CellBroadcastService {
    private GsmCellBroadcastHandler mGsmCellBroadcastHandler;
    private CellBroadcastHandler mCdmaCellBroadcastHandler;
    private CdmaServiceCategoryProgramHandler mCdmaScpHandler;

    private static final String TAG = "DefaultCellBroadcastService";

    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    @Override
    public void onCreate() {
        super.onCreate();
        mGsmCellBroadcastHandler =
                GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(getApplicationContext());
        mCdmaCellBroadcastHandler =
                CellBroadcastHandler.makeCellBroadcastHandler(getApplicationContext());
        mCdmaScpHandler =
                CdmaServiceCategoryProgramHandler.makeScpHandler(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        mGsmCellBroadcastHandler.cleanup();
        mCdmaCellBroadcastHandler.cleanup();
        super.onDestroy();
    }

    @Override
    public void onGsmCellBroadcastSms(int slotIndex, byte[] message) {
        Log.d(TAG, "onGsmCellBroadcastSms received message on slotId=" + slotIndex);
        mGsmCellBroadcastHandler.onGsmCellBroadcastSms(slotIndex, message);
    }

    @Override
    public void onCdmaCellBroadcastSms(int slotIndex, byte[] bearerData, int serviceCategory) {
        Log.d(TAG, "onCdmaCellBroadcastSms received message on slotId=" + slotIndex);

        int subId = CellBroadcastHandler.getSubIdForPhone(getApplicationContext(), slotIndex);

        String plmn = "";
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            plmn = getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(subId).getNetworkOperator();
        }

        SmsCbMessage message = parseCdmaBroadcastSms(getApplicationContext(), slotIndex, plmn,
                bearerData, serviceCategory);
        if (message != null) {
            CellBroadcastServiceMetrics.getInstance().logMessageReported(getApplicationContext(),
                    RPT_CDMA, SRC_CBS, message.getSerialNumber(), message.getServiceCategory());
            mCdmaCellBroadcastHandler.onCdmaCellBroadcastSms(message);
        }
    }

    @Override
    public void onCdmaScpMessage(int slotIndex, List<CdmaSmsCbProgramData> programData,
            String originatingAddress, Consumer<Bundle> callback) {
        Log.d(TAG, "onCdmaScpMessage received message on slotId=" + slotIndex);
        mCdmaScpHandler.onCdmaScpMessage(slotIndex, new ArrayList<>(programData),
                originatingAddress, callback);
    }

    @Override
    public @NonNull String getCellBroadcastAreaInfo(int slotIndex) {
        Log.d(TAG, "getCellBroadcastAreaInfo on slotId=" + slotIndex);
        return mGsmCellBroadcastHandler.getCellBroadcastAreaInfo(slotIndex);
    }

    /**
     * Parses a CDMA broadcast SMS
     *
     * @param slotIndex       the slotIndex the SMS was received on
     * @param plmn            the PLMN for a broadcast SMS or "" if unknown
     * @param bearerData      the bearerData of the SMS
     * @param serviceCategory the service category of the broadcast
     */
    @VisibleForTesting
    public static SmsCbMessage parseCdmaBroadcastSms(Context context, int slotIndex, String plmn,
            byte[] bearerData,
            int serviceCategory) {
        BearerData bData;
        try {
            bData = BearerData.decode(context, bearerData, serviceCategory);
        } catch (Exception e) {
            final String errorMessage = "Error decoding bearer data e=" + e.toString();
            Log.e(TAG, errorMessage);
            CellBroadcastServiceMetrics.getInstance()
                    .logMessageError(ERR_CDMA_DECODING, errorMessage);
            return null;
        }
        Log.d(TAG, "MT raw BearerData = " + toHexString(bearerData, 0, bearerData.length));
        SmsCbLocation location = new SmsCbLocation(plmn, -1, -1);

        int subId = CellBroadcastHandler.getSubIdForPhone(context, slotIndex);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        }

        return new SmsCbMessage(SmsCbMessage.MESSAGE_FORMAT_3GPP2,
                SmsCbMessage.GEOGRAPHICAL_SCOPE_PLMN_WIDE, bData.messageId, location,
                serviceCategory, bData.getLanguage(), bData.userData.msgEncoding,
                bData.userData.payloadStr, bData.priority, null, bData.cmasWarningInfo, 0, null,
                System.currentTimeMillis(), slotIndex, subId);
    }

    private static String toHexString(byte[] array, int offset, int length) {
        char[] buf = new char[length * 2];
        int bufIndex = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = array[i];
            buf[bufIndex++] = HEX_DIGITS[(b >>> 4) & 0x0F];
            buf[bufIndex++] = HEX_DIGITS[b & 0x0F];
        }
        return new String(buf);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("DefaultCellBroadcastService:");
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED);
        writer.println(
                "  defaultCBRPackageName=" + GsmCellBroadcastHandler.getDefaultCBRPackageName(
                        getApplicationContext(), intent));
        if (mGsmCellBroadcastHandler != null) {
            mGsmCellBroadcastHandler.dump(fd, writer, args);
        } else {
            writer.println("  mGsmCellBroadcastHandler is null");
        }
        if (mCdmaCellBroadcastHandler != null) {
            mCdmaCellBroadcastHandler.dump(fd, writer, args);
        } else {
            writer.println("  mCdmaCellBroadcastHandler is null");
        }
        if (mCdmaScpHandler != null) {
            mCdmaScpHandler.dump(fd, writer, args);
        } else {
            writer.println("  mCdmaScpHandler is null");
        }
    }
}
