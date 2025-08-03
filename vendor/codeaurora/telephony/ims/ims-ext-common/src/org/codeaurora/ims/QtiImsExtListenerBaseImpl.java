/* Copyright (c) 2015-2017, 2019-2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import org.codeaurora.ims.internal.IQtiImsExtListener;
import android.telephony.ims.ImsCallForwardInfo;

/**
 * This class contains default implementation for IQtiImsExtListener.
 */
public class QtiImsExtListenerBaseImpl extends IQtiImsExtListener.Stub {

    @Override
    public void onSetCallForwardUncondTimer(int phoneId, int status) {
    }

    @Override
    public void onGetCallForwardUncondTimer(int phoneId, int startHour, int endHour,
            int startMinute, int endMinute, int reason, int status, String number, int service) {
    }

    @Override
    public void onUTReqFailed(int phoneId, int errCode, String errString) {
    }

    @Override
    public void receiveCancelModifyCallResponse(int phoneId, int result) {
    }

    @Override
    public void notifyVopsStatus(int phoneId, boolean vopsStatus) {
    }

    @Override
    public void notifySsacStatus(int phoneId, boolean ssacStatusResponse) {
    }

    @Override
    public void notifyParticipantStatusInfo(int phoneId, int operation, int sipStatus,
            String participantUri, boolean isEct) {
    }

    @Override
    public void onVoltePreferenceUpdated(int phoneId, int result) {
    }

    @Override
    public void onVoltePreferenceQueried(int phoneId, int result, int mode) {
    }

    @Override
    public void onSetHandoverConfig(int phoneId, int result) {
    }

    @Override
    public void onGetHandoverConfig(int phoneId, int result, int hoConfig) {
    }

    /**
     * This API is invoked when USSD put on IMS pipe at network fails
     * and is a temporary solution to notify clients about failures as
     * current vendor implementation doesn't support USSD over IMS
     * request from client.
     * @param type - should be used to map USSD UNSOL on CS pipe with
     *        USSD put on IMS pipe at network failure.
     */
    @Override
    public void onUssdFailed(int phoneId, int type, int errorCode,
            String errorMessage) {
    }

    @Override
    public void queryCallForwardStatusResponse(int phoneId, ImsCallForwardInfo[] cfInfoList) {
    }

    @Override
    public void queryCallBarringResponse(int[] response) {
    }

    @Override
    public void onScbmExited(boolean status) {
    }

    @Override
    public void notifyDataChannelCapability(int phoneId, boolean dcCapability) {
    }

    @Override
    public void handleSendVosSupportStatusResponse(int phoneId, int result) {
    }

    @Override
    public void handleSendVosActionInfoResponse(int phoneId, int result) {
    }
}

