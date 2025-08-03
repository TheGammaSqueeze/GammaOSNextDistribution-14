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

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import org.codeaurora.ims.internal.IImsMultiIdentityInterface;
import org.codeaurora.ims.internal.IImsMultiIdentityListener;
import android.util.Log;
import java.util.ArrayList;

public class ImsMultiIdentityManager {
    private static final String LOG_TAG = "ImsMultiIdentityManager";

    public final static int REGISTRATION_RESPONSE_FAILURE = 0;
    public final static int REGISTRATION_RESPONSE_SUCCESS = 1;

    private QtiImsExtManager  mQtiImsExtMgr;
    private volatile IImsMultiIdentityInterface mInterface;
    private int mPhoneId;

    ImsMultiIdentityManager(int phoneId, QtiImsExtManager imsExtMgr) {
        mPhoneId = phoneId;
        mQtiImsExtMgr = imsExtMgr;
        mQtiImsExtMgr.addCleanupListener(()->{mInterface = null;});
    }

    private IImsMultiIdentityInterface getMultiIdentityInterface() throws QtiImsException{
        IImsMultiIdentityInterface intf = mInterface;
        if (intf != null) {
            return intf;
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        intf = mQtiImsExtMgr.getMultiIdentityInterface(mPhoneId);
        if (intf == null) {
            Log.e(LOG_TAG, "mInterface is NULL");
            throw new QtiImsException("Remote Interface is NULL");
        }
        mInterface = intf;
        return intf;
    }

    public void setMultiIdentityListener(ImsMultiIdentityListenerBase listener)
            throws QtiImsException{
        if (listener == null) {
            String msg = "setMultiIdentityListener :: listener is NULL";
            Log.e(LOG_TAG, msg);
            throw new QtiImsException(msg);
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getMultiIdentityInterface().setMultiIdentityListener(listener.getListener());
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setMultiIdentityListener : " + e);
        }
    }

    public void updateRegistrationStatus(
            ArrayList<MultiIdentityLineInfo> linesInfo) throws QtiImsException{
        if (linesInfo == null) {
            String msg = "updateRegistrationStatus :: linesInfo is NULL";
            Log.e(LOG_TAG, msg);
            throw new QtiImsException(msg);
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getMultiIdentityInterface().updateRegistrationStatus(linesInfo);
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService updateRegistrationStatus : " + e);
        }
    }

    public void queryVirtualLineInfo(String msisdn) throws QtiImsException{
        if (msisdn == null || msisdn.isEmpty()) {
            String msg = "queryVirtualLineInfo :: invalid msisdn";
            Log.e(LOG_TAG, msg);
            throw new QtiImsException(msg);
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getMultiIdentityInterface().queryVirtualLineInfo(msisdn);
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVirtualLineInfo : " + e);
        }
    }
}
