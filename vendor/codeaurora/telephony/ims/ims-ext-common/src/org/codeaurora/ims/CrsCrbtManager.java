/* Copyright (c) 2021 The Linux Foundation. All rights reserved.
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
import org.codeaurora.ims.internal.ICrsCrbtController;
import org.codeaurora.ims.internal.ICrsCrbtListener;
import android.util.Log;

public class CrsCrbtManager {
    private static final String LOG_TAG = "CrsCrbtManager";

    private QtiImsExtManager mQtiImsExtMgr;
    private volatile ICrsCrbtController mInterface;
    private int mPhoneId;

    CrsCrbtManager(int phoneId, QtiImsExtManager imsExtMgr) {
        mPhoneId = phoneId;
        mQtiImsExtMgr = imsExtMgr;
        mQtiImsExtMgr.addCleanupListener(()->{mInterface = null;});
    }

    private ICrsCrbtController getBinder() throws QtiImsException{
        ICrsCrbtController intf = mInterface;
        if (intf != null) {
            return intf;
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        intf = mQtiImsExtMgr.getCrsCrbtController(mPhoneId);
        if (intf == null) {
            Log.e(LOG_TAG, "mInterface is NULL");
            throw new QtiImsException("Remote Interface is NULL");
        }
        mInterface = intf;
        return intf;
    }

    /**
     * Used by client to register call back listener with vendor for CRS
     * type/silencUi/SipDtmf state
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener, to get notified for CRS type or silenceUi or SipDtmf change.
     * @return void
     *
     */
    public void setCrsCrbtListener(CrsCrbtListenerBase listener)
        throws QtiImsException {
      mQtiImsExtMgr.validateInvariants(mPhoneId);
      try {
          getBinder().setCrsCrbtListener(listener == null ? null : listener.getBinder());
      } catch(RemoteException e) {
          throw new QtiImsException("Remote ImsService setCrsCrbtListener: " + e);
      }
    }

    /**
     * Used by client to unregister call back listener with vendor for CRS
     * type/silencUi/SipDtmf state
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener, to unregister.
     * @return void
     *
     */
    public void removeCrsCrbtListener(CrsCrbtListenerBase listener)
        throws QtiImsException {
      mQtiImsExtMgr.validateInvariants(mPhoneId);
      try {
          getBinder().removeCrsCrbtListener(listener == null ? null : listener.getBinder());
      } catch(RemoteException e) {
          throw new QtiImsException("Remote ImsService removeCrsCrbtListener: " + e);
      }
    }

    /**
     * isPreparatorySession
     * whether show Video CRS Ui
     *
     * @param callId
     * @return boolean
     */
    public boolean isPreparatorySession(String callId)
        throws QtiImsException{
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            return getBinder().isPreparatorySession(callId);
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService isPreparatorySession : " + e);
        }
    }

    /**
     * Used by client to send dtmf string to RIL
     */
    public void sendSipDtmf(String requestCode)
        throws QtiImsException {
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getBinder().sendSipDtmf(requestCode);
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendSipDtmf : " + e);
        }
    }
}
