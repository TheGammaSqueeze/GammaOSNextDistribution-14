/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
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


/**
 * Interface used to interact with the telephony framework for
 * Telephony value adds.
 * {@hide}
 */
interface IExtTelephony {

    /**
    * Get logical phone id for Emergency call.
    * @param - void
    * @return phone id
    * Requires permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    int getPhoneIdForECall();

    /**
    * Check if get the icc file handler from specific application family sucessfully
    * @param slotId user preferred slotId.
    * @param family UICC application family.
    * @return true or false
    * Requires permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    boolean hasGetIccFileHandler(int slotId, int family);

    /**
    * Read the icc transparent file in the SIM card.
    * @param slotId user preferred slotId.
    * @param family UICC application family.
    * @param efId the file ID in the SIM card.
    * @return true or false
    * Requires permission: android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE
    */
    boolean readEfFromIcc(int slotId, int family, int efId);

    /**
    * Write the icc transparent file in the SIM card.
    * @param slotId user preferred slotId.
    * @param family UICC application family.
    * @param efId the file ID in the SIM card.
    * @param efdata updated data to the EF.
    * @return true - send the request to load transparent files sucessfully
    *         false - failed to get the icc file handler
    * Requires permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    boolean writeEfToIcc(int slotId, int family, int efId, in byte[] efData);
}
