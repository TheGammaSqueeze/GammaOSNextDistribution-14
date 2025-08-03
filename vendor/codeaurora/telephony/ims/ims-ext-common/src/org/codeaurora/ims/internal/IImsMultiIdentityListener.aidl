/* Copyright (c) 2018, The Linux Foundation. All rights reserved.
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

import java.util.List;
import org.codeaurora.ims.MultiIdentityLineInfo;
/**
 * Used by client application to get the result from lower layer by
 * communicating with vendor.
 * {@hide}
 */
oneway interface IImsMultiIdentityListener {

   /*
    * Implemented by the MultiIdentity Client. Used by ATEL to propagate
    * Registration response to the client
    * @param phoneId - Denotes the phoneId
    * @param response - Registration response success/failure
    */
    void onUpdateRegistrationInfoResponse(int phoneId, int response);

   /*
    * Implemented by the MultiIdentity Client. Used by ATEL to propagate
    * Registration status change indication to the client
    * @param phoneId - Denotes the phoneId
    * @param info - Line information which contatins the registration status
    */
    void onRegistrationStatusChange(int phoneId, in List<MultiIdentityLineInfo> info);

   /*
    * Implemented by the MultiIdentity Client. Used by ATEL to propagate
    * the virtual line info response to the client
    * @param phoneId - Denotes the phoneId
    * @param msisdn - msisdn of the line whose information is being queried
    * @param info - Virtual Line Info response
    */
    void onQueryVirtualLineInfoResponse(int phoneId, String msisdn, in List<String> pAssociatedUri);
}
