/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.RemoteException;
import android.telephony.CellInfo;
import android.util.Log;
import com.qti.extphone.BearerAllocationStatus;
import com.qti.extphone.DcParam;
import com.qti.extphone.DualDataRecommendation;
import com.qti.extphone.IExtPhoneCallback;
import com.qti.extphone.NetworkSelectionMode;
import com.qti.extphone.NrConfig;
import com.qti.extphone.NrConfigType;
import com.qti.extphone.NrIconType;
import com.qti.extphone.QRadioResponseInfo;
import com.qti.extphone.SignalStrength;
import com.qti.extphone.Status;
import com.qti.extphone.Token;
import com.qti.extphone.UpperLayerIndInfo;
import java.util.List;

public class ExtPhoneCallbackBase extends IExtPhoneCallback.Stub {
    private static final String TAG = "ExtPhoneCallbackBase";

    @Override
    public void onNrIconType(int slotId, Token token, Status status, NrIconType
            nrIconType) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrIconType: slotId = " + slotId + " token = " + token +
                " status = " + status + " NrIconType = " + nrIconType);
    }

    @Override
    public void onEnableEndc(int slotId, Token token, Status status) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onEnableEndc: slotId = " + slotId + " token = " + token +
                " status = " + status);
    }

    @Override
    public void onEndcStatus(int slotId, Token token, Status status, boolean enableStatus) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onEndcStatus: slotId = " + slotId + " token = " + token +
                " status = " + status + " enableStatus = " + enableStatus);
    }

    @Override
    public void onSetNrConfig(int slotId, Token token, Status status) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSetNrConfig: slotId = " + slotId + " token = " + token +
                " status = " +status);
    }

    @Override
    public void onNrConfigStatus(int slotId, Token token, Status status, NrConfig nrConfig) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrConfigStatus: slotId = " + slotId + " token = " + token +
                " status = " + status + " NrConfig = " + nrConfig);
    }

    @Override
    public void sendCdmaSmsResponse(int slotId, Token token, Status status, SmsResult sms) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: sendCdmaSmsResponse: slotId = " + slotId +
                " token = " + token + " status = " + status + " SmsResult = " + sms);
    }

    @Override
    public void on5gStatus(int slotId, Token token, Status status, boolean enableStatus) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: on5gStatus: slotId = " + slotId + " token = " + token +
                " status" + status + " enableStatus = " + enableStatus);
    }

    @Override
    public void onAnyNrBearerAllocation(int slotId, Token token, Status status,
                                        BearerAllocationStatus bearerStatus) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrBearerAllocationChange: slotId = " + slotId +
                " token = " + token + " status = " + status + " bearerStatus = " + bearerStatus);
    }

    @Override
    public void getQtiRadioCapabilityResponse(int slotId, Token token, Status status, int raf)
            throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: getQtiRadioCapabilityResponse: slotId = " + slotId +
                " token = " + token + " status" + status + " raf = " + raf);
    }

    @Override
    public void getQosParametersResponse(int slotId, Token token, Status status,
                QosParametersResult result) throws RemoteException {
    }

    public void onNrDcParam(int slotId, Token token, Status status, DcParam dcParam) throws
            RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onNrDcParam: slotId = " + slotId +
                " token = " + token + " status" + status + " dcParam = " + dcParam);
    }

    @Override
    public void onUpperLayerIndInfo(int slotId, Token token, Status status,
            UpperLayerIndInfo uilInfo) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onUpperLayerIndInfo: slotId = " + slotId +
                " token = " + token + " " + "status" + status +
                " UpperLayerIndInfo = " + uilInfo);
    }

    @Override
    public void on5gConfigInfo(int slotId, Token token, Status status, NrConfigType
            nrConfigType) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: on5gConfigInfo: slotId = " + slotId + " token = " +
                token + " " + "status" + status + " NrConfigType = " + nrConfigType);
    }

    @Override
    public void onSignalStrength(int slotId, Token token, Status status, SignalStrength
            signalStrength) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: onSignalStrength: slotId = " + slotId +
                " token = " + token + " " + "status" + status +
                " signalStrength = " + signalStrength);
    }

    @Override
    public void setCarrierInfoForImsiEncryptionResponse(int slotId, Token token,
            QRadioResponseInfo info) throws RemoteException {
        Log.d(TAG, "UNIMPLEMENTED: setCarrierInfoForImsiEncryptionResponse: slotId = " + slotId +
                " token = " + token + " info = " + info);
    }

    @Override
    public void queryCallForwardStatusResponse(Status status, QtiCallForwardInfo[] infos) throws
            RemoteException {
        Log.d(TAG, "queryCallForwardStatusResponse: status = " + status + " CallForwardInfo = "
                 + infos);
    }

    @Override
    public void getFacilityLockForAppResponse(Status status, int[] response) throws
            RemoteException {
        Log.d(TAG, "getFacilityLockForAppResponse: status = " + status + " response = "
                 + response);
    }

    @Override
    public void setSmartDdsSwitchToggleResponse(Token token, boolean result) throws
            RemoteException {
        Log.d(TAG, "setSmartDdsSwitchToggleResponse: token = " + token +  " result = " + result);
    }

    @Override
    public void onImeiTypeChanged(QtiImeiInfo[] imeiInfo) throws RemoteException {
    }

    public void onSendUserPreferenceForDataDuringVoiceCall(int slotId, Token token,
            Status status) throws RemoteException {
    }

    @Override
    public void onDdsSwitchCapabilityChange(int slotId, Token token, Status status,
            boolean support) throws RemoteException {
    }

    @Override
    public void onDdsSwitchCriteriaChange(int slotId, boolean telephonyDdsSwitch)
            throws RemoteException {
    }

    @Override
    public void onDdsSwitchRecommendation(int slotId,
            int recommendedSlotId) throws RemoteException {
    }

    @Override
    public void onDataDeactivateDelayTime(int slotId, long delayTimeMilliSecs)
            throws RemoteException {
    }

    @Override
    public void onEpdgOverCellularDataSupported(int slotId, boolean support)
            throws RemoteException {
    }

    @Override
    public void getSecureModeStatusResponse(Token token, Status status, boolean enableStatus)
            throws RemoteException {
    }

    @Override
    public void onSecureModeStatusChange(boolean enabled) throws RemoteException {
    }

    @Override
    public void startNetworkScanResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
    }

    @Override
    public void stopNetworkScanResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
    }

    @Override
    public void setNetworkSelectionModeManualResponse(int slotId, Token token, int errorCode) throws
            RemoteException {
    }

    @Override
    public void setNetworkSelectionModeAutomaticResponse(int slotId, Token token,
            int errorCode) throws RemoteException {
    }

    @Override
    public void getNetworkSelectionModeResponse(int slotId, Token token, Status status,
            NetworkSelectionMode modes) throws RemoteException {
    }

    @Override
    public void networkScanResult(int slotId, Token token, int status, int error,
            List<CellInfo> cellInfos) throws RemoteException {
    }

    @Override
    public void setMsimPreferenceResponse(Token token, Status status) throws RemoteException {
    }

    @Override
    public void onQosParametersChanged(int slotId, int cid, QosParametersResult result)
            throws RemoteException {
    }

    @Override
    public void setSimTypeResponse(Token token, Status status) throws RemoteException {
    }

    @Override
    public void onSimTypeChanged(QtiSimType[] simtype) throws RemoteException {
    }

    @Override
    public void onDualDataCapabilityChanged(Token token, Status status, boolean support)
            throws RemoteException {
    }

    @Override
    public void setDualDataUserPreferenceResponse(Token token, Status status)
            throws RemoteException {
    }

    @Override
    public void onDualDataRecommendation(DualDataRecommendation rec)
            throws RemoteException {
    }

    @Override
    public void onSimPersoUnlockStatusChange(int slotId, QtiPersoUnlockStatus persoUnlockStatus)
            throws RemoteException {
    }
}
