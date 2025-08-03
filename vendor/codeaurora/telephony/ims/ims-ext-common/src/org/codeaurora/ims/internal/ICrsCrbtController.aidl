/* Copyright (c) 2021, The Linux Foundation. All rights reserved.
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
 */

package org.codeaurora.ims.internal;

import org.codeaurora.ims.internal.ICrsCrbtListener;

/**
 * Used by client application to communicate with vendor code
 * {@hide}
 */
interface ICrsCrbtController {
    /**
     * Used by client to register call back listener with vendor for CRS
     * type/silencUi state
     *
     * @param listener, to get notified for CRS type or silenceUi change.
     * @return void
     *
     */
    oneway void setCrsCrbtListener(ICrsCrbtListener listener);

    /**
     * Used by client to unregister call back listener with vendor for CRS
     * type/silencUi state
     *
     * @param listener, to be unregister.
     * @return void
     *
     */
    oneway void removeCrsCrbtListener(ICrsCrbtListener listener);

    /**
     * isPreparatorySession
     * whether show Video CRS Ui
     *
     * @param callId indicates the phone instance which triggered the request
     * @return boolean
     */
    boolean isPreparatorySession(String callId);

    /**
     * Used by client for sending sip dtmf for video CRS/CRBT
     */
    oneway void sendSipDtmf(String requestCode);
}
