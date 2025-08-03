/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package org.codeaurora.ims;

import android.content.Context;
import android.os.RemoteException;
import org.codeaurora.ims.internal.IImsArListener;
import org.codeaurora.ims.internal.IImsArController;
import org.codeaurora.ims.utils.QtiImsExtUtils;

import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;


public abstract class ImsArControllerBase {
    public final class ArBinder extends IImsArController.Stub {

        @Override
        public void setListener(
                IImsArListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() -> {
                    try {
                        ImsArControllerBase.this.onSetListener(listener);
                    } catch (RemoteException e) {
                        throw new CompletionException(e);
                    }},
                    "setListener", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void enableArMode(String cameraId) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    ImsArControllerBase.this.onEnableArMode(cameraId),
                    "enableArMode", mExecutor, QtiImsExtUtils.MODIFY_PHONE_STATE,
                    mContext);
        }

        @Override
        public void setLocalRenderingDelay(int delay) throws RemoteException {
           QtiImsExtUtils.executeMethodAsync(() ->
                    ImsArControllerBase.this.onSetLocalRenderingDelay(delay),
                    "setLocalRenderingDelay", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }
    }

    private IImsArController mBinder;
    private Executor mExecutor;
    private Context mContext;

    public IImsArController getBinder() {
        if (mBinder == null) {
            mBinder = new ArBinder();
        }
        return mBinder;
    }

    public ImsArControllerBase(Executor executor, Context context) {
        mExecutor = executor;
        mContext = context;
    }

    protected void onSetListener(
            IImsArListener listener) throws RemoteException {
        //no-op
    }

    protected void onEnableArMode(String cameraId){
        //no-op
    }

    protected void onSetLocalRenderingDelay(int delay) {
        //no-op
    }
}
