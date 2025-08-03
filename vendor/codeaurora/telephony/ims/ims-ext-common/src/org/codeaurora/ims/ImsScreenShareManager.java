/* Copyright (c) 2019 The Linux Foundation. All rights reserved.
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

import android.os.RemoteException;
import org.codeaurora.ims.internal.IImsScreenShareController;
import org.codeaurora.ims.internal.IImsScreenShareListener;
import android.util.Log;

public class ImsScreenShareManager {
    private static final String LOG_TAG = "ImsScreenShareManager";

    private QtiImsExtManager mQtiImsExtMgr;
    private volatile IImsScreenShareController mInterface;
    private int mPhoneId;

    ImsScreenShareManager(int phoneId, QtiImsExtManager imsExtMgr) {
        mPhoneId = phoneId;
        mQtiImsExtMgr = imsExtMgr;
        mQtiImsExtMgr.addCleanupListener(()->{mInterface = null;});
    }

    private IImsScreenShareController getBinder() throws QtiImsException{
        IImsScreenShareController intf = mInterface;
        if (intf != null) {
            return intf;
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        intf = mQtiImsExtMgr.getScreenShareController(mPhoneId);
        if (intf == null) {
            Log.e(LOG_TAG, "mInterface is NULL");
            throw new QtiImsException("Remote Interface is NULL");
        }
        mInterface = intf;
        return intf;
    }

    /**
     * Used by client to set listener for screen share
     * in vendor. Current implementation doesn't allow
     * to set the listener as null and lower layer
     * would overwrite the previous listener if this
     * API is invoked again.
     */
    public void setScreenShareListener(ImsScreenShareListenerBase listener)
            throws QtiImsException{
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        if (listener == null) {
            Log.e(LOG_TAG, "listener is NULL");
            throw new QtiImsException("Listener is NULL");
        }
        try {
            getBinder().setScreenShareListener(listener.getBinder());
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setScreenShareListener : " + e);
        }
    }

    /**
     * Used by client to start screen share with display width and
     * height.
     */
    public void startScreenShare(int width, int height) throws QtiImsException{
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getBinder().startScreenShare(width, height);
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService startScreenShare : " + e);
        }
    }

    /**
     * Used by client to stop screen share.
     */
    public void stopScreenShare() throws QtiImsException{
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getBinder().stopScreenShare();
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService stopScreenShare : " + e);
        }
    }
}
