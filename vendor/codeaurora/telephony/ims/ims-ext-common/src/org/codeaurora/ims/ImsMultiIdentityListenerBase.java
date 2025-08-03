/* Copyright (c) 2018 The Linux Foundation. All rights reserved.
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

package org.codeaurora.ims;

import java.util.List;
import org.codeaurora.ims.MultiIdentityLineInfo;
import org.codeaurora.ims.internal.IImsMultiIdentityListener;

public abstract class ImsMultiIdentityListenerBase {

    private final class MultiIdentityListener extends IImsMultiIdentityListener.Stub {

        public void onUpdateRegistrationInfoResponse(int phoneId, int response){
            ImsMultiIdentityListenerBase.this.
                    onUpdateRegistrationInfoResponse(phoneId, response);
        }

        public void onRegistrationStatusChange(int phoneId,
                List<MultiIdentityLineInfo> info) {
            ImsMultiIdentityListenerBase.this.
                    onRegistrationStatusChange(phoneId, info);
        }

        public void onQueryVirtualLineInfoResponse(int phoneId,
                String msisdn,
                List<String> pAssociatedUri) {
            ImsMultiIdentityListenerBase.this.
                onQueryVirtualLineInfoResponse(phoneId, msisdn, pAssociatedUri);
        }
    }

    private MultiIdentityListener mListener;

    public IImsMultiIdentityListener getListener() {
        if (mListener == null) {
            mListener = new MultiIdentityListener();
        }
        return mListener;
    }

    protected void onUpdateRegistrationInfoResponse(int phoneId, int response){
        // no-op
    }

    protected void onRegistrationStatusChange(int phoneId,
            List<MultiIdentityLineInfo> info) {

        // no-op
    }

    protected void onQueryVirtualLineInfoResponse(int phoneId,
            String msisdn,
            List<String> pAssociatedUri) {
        // no-op
    }
}
