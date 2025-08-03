/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import android.os.RemoteException;
import org.codeaurora.ims.internal.IImsArController;
import org.codeaurora.ims.internal.IImsArListener;
import android.util.Log;

public class ImsArManager {
    private static final String LOG_TAG = "ImsArManager";

    private QtiImsExtManager mQtiImsExtMgr;
    private volatile IImsArController mInterface;
    private int mPhoneId;

    ImsArManager(int phoneId, QtiImsExtManager imsExtMgr) {
        mPhoneId = phoneId;
        mQtiImsExtMgr = imsExtMgr;
        mQtiImsExtMgr.addCleanupListener(()->{mInterface = null;});
    }

    private IImsArController getBinder() throws QtiImsException {
        IImsArController intf = mInterface;
        if (intf != null) {
            return intf;
        }
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        intf = mQtiImsExtMgr.getArController(mPhoneId);
        if (intf == null) {
            Log.e(LOG_TAG, "mInterface is NULL");
            throw new QtiImsException("Remote Interface is NULL");
        }
        mInterface = intf;
        return intf;
    }

    /**
     * Used by client to set listener for Ar call
     * in vendor. Current implementation doesn't allow
     * to set the listener as null and lower layer
     * would overwrite the previous listener if this
     * API is invoked again.
     */
    public void setListener(ImsArListenerBase listener)
            throws QtiImsException {
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        if (listener == null) {
            Log.e(LOG_TAG, "listener is NULL");
            throw new QtiImsException("Listener is NULL");
        }
        try {
            getBinder().setListener(listener.getBinder());
        }
        catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setListener : " + e);
        }
    }

    /**
     * Used by client to start/stop AR call.
     * cameraId is null to stop AR. cameraId is not null to
     * start AR with front/rear camera.
     */
    public void enableArMode(String cameraId) throws QtiImsException {
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getBinder().enableArMode(cameraId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService enableArMode: " + e);
        }
    }

    /**
     * Used by client to set the delay of local rendering.
     * The delay is measured in millisecond.
     */
    public void setLocalRenderingDelay(int delay) throws QtiImsException {
        mQtiImsExtMgr.validateInvariants(mPhoneId);
        try {
            getBinder().setLocalRenderingDelay(delay);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setLocalRenderingDelay : " + e);
        }
    }
}
