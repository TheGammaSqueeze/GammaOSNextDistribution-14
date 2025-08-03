/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
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
 */

package org.codeaurora.internal;

import org.codeaurora.internal.SignalStrength;
import org.codeaurora.internal.DcParam;
import org.codeaurora.internal.Status;
import org.codeaurora.internal.Token;
import org.codeaurora.internal.BearerAllocationStatus;
import org.codeaurora.internal.UpperLayerIndInfo;
import org.codeaurora.internal.NrConfig;
import org.codeaurora.internal.NrConfigType;
import org.codeaurora.internal.NrIconType;


interface INetworkCallback {
    /**
    * @deprecated
    */
    void on5gStatus(int slotId, in Token token, in Status status, boolean enableStatus);

    /**
    *@deprecated
    */
    void onNrDcParam(int slotId, in Token token, in Status status, in DcParam dcParam);

    /**
    * @deprecated
    */
    void onAnyNrBearerAllocation(int slotId, in Token token, in Status status,
            in BearerAllocationStatus bearerStatus);

    /**
    * @deprecated
    */
    void onUpperLayerIndInfo(int slotId, in Token token, in Status status,
            in UpperLayerIndInfo upperLayerInfo);

    /**
    * @deprecated
    */
    void onSignalStrength(int slotId, in Token token, in Status status,
            in SignalStrength signalStrength);

    /**
    * @deprecated
    * use onNrConfigStatus instead to get current nr config.
    */
    void on5gConfigInfo(int slotId, in Token token, in Status status,
            in NrConfigType nrConfigType);

    void onNrIconType(int slotId, in Token token, in Status status,
            in NrIconType nrIconType);

    /**
    * Response to enableEndc
    * @param - slotId
    * @param - token is the same token which is recived in enableEndc
    * @param - status SUCCESS/FAILURE based on the modem Result code
    */
    void onEnableEndc(int slotId, in Token token, in Status status);

    /**
    * Response to queryEndcStatus
    * @param - slotId
    * @param - token is the same token which is recived in queryEndcStatus
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - enableStatus true if endce is enabled otherwise false
    */
    void onEndcStatus(int slotId, in Token token, in Status status, boolean enableStatus);

    /**
    * Response to setNrConfig
    * @param - slotId
    * @param - token is the same token which is recived in setNrConfig
    * @param - status SUCCESS/FAILURE based on the modem Result code
    */
    void onSetNrConfig(int slotId, in Token token, in Status status);

    /**
    * Response to queryNrConfig
    * @param - slotId
    * @param - token is the same token which is recived in queryNrConfig
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - nrConfig: NSA + SA/NSA/SA
    */
    void onNrConfigStatus(int slotId, in Token token, in Status status, in NrConfig nrConfig);
}
