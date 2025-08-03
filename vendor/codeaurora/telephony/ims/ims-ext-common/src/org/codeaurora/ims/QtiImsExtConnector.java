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
package org.codeaurora.ims;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.codeaurora.ims.internal.IQtiImsExt;

/**
 * This class is starting point for all the QtiImsExt actions.
 * You can acquire an instance of QtiImsExtManager by calling {@link connect connect()}
 */
public class QtiImsExtConnector {

    private static final String LOG_TAG = "QtiImsExtConnector";

    private final Context mContext;
    private IListener mListener;
    private QtiImsExtManager mQtiImsExtManager;
    private IQtiImsExt mQtiImsExt;
    private boolean mBound = false;
    private Handler mConnectionRetryHandler = null;
    private static final int QTI_IMS_RETRY_TIMEOUT_MS = 500;
    private static final int QTI_IMS_EXT_SERVICE_CONNECT = 1;

    /** Used to update the service connection status to clients.
      */
    public interface IListener {
        /** Called when QtiIMsExtService is connected.
          * @param qtiImsExtManager: QtiImsExtManager instance.
          */
        void onConnectionAvailable(QtiImsExtManager manager);

        /** Called when QtiIMsExtService is disconnected.
         */
        void onConnectionUnavailable();
    }

    public QtiImsExtConnector(Context context, IListener listener) throws QtiImsException {
        this(context, listener, Looper.myLooper());
    }

    public QtiImsExtConnector(Context context, IListener listener, Looper looper)
            throws QtiImsException {
        if (context == null || listener == null || looper == null) {
            throw new QtiImsException("context, listener and looper should not be null ");
        }
        mContext = context;
        mListener = listener;
        mConnectionRetryHandler = new ConnectionRetryHandler(looper);
    }

    /* Bind to QtiImsExtService */
    public void connect() {
        bindQtiImsExtService();
    }

    /* This will help to cleanup QtiImsExtManager/QtiImsExtService. */
    public void disconnect() {
        mListener = null;
        unbindQtiImsExtService();
        cleanUp();
    }

    private void cleanUp() {
        mConnectionRetryHandler.removeMessages(QTI_IMS_EXT_SERVICE_CONNECT);
        mQtiImsExt = null;
        if (mQtiImsExtManager != null) {
            mQtiImsExtManager.dispose();
        }
    }

    /**
     * Bind to QtiImsExtService.
     */
    private void bindQtiImsExtService() {
        Intent intent = new Intent();
        intent.setClassName("org.codeaurora.ims", "org.codeaurora.ims.QtiImsExtService");

        mBound = mContext.bindService(intent, mQtiImsExtServiceConnection,
                Context.BIND_AUTO_CREATE);
        Log.d(LOG_TAG, "Attempt to bind QtiImsExt service returned with: " + mBound);
        //In general bindService will return true,
        //if returned false due to some issue retry binding.
        if (mBound == false) {
            mConnectionRetryHandler.sendEmptyMessageDelayed(QTI_IMS_EXT_SERVICE_CONNECT,
                    QTI_IMS_RETRY_TIMEOUT_MS);
        }
    }

    /**
     * unbind to QtiImsExtService.
     */
    private void unbindQtiImsExtService() {
        if (mContext != null && mBound) {
            mContext.unbindService(mQtiImsExtServiceConnection);
            mBound = false;
        }
    }

    // Handler used to retry conencting to QtiImsExtService
    private class ConnectionRetryHandler extends Handler {
        public ConnectionRetryHandler() {
            super();
        }

        public ConnectionRetryHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case QTI_IMS_EXT_SERVICE_CONNECT:
                    bindQtiImsExtService();
                    break;
                default:
            }
        }
    }

    /** Service connection */
    private ServiceConnection mQtiImsExtServiceConnection = new ServiceConnection() {
        /* Notify to clients when QtiImsExtService is connected*/
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            mQtiImsExt = IQtiImsExt.Stub.asInterface(b);
            Log.d(LOG_TAG, "onServiceConnected");
            if (mQtiImsExt == null) {
                Log.e(LOG_TAG, "QtiImsExtService is not running");
                return;
            }

            mQtiImsExtManager = new QtiImsExtManager(mContext, mQtiImsExt);

            if (mListener != null) {
                mListener.onConnectionAvailable(mQtiImsExtManager);
            }
        }

        /* Notify to clients when QtiImsExtService is disconnected*/
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(LOG_TAG, "onServiceDisconnected " + mListener);
            cleanUp();
            if (mListener != null) {
                mListener.onConnectionUnavailable();
            }
        }

        /** This will be called when QtiImsExtService onBind returns null,
          * which will happen if ImsService is not started by the time QtiImsExtService is started.
          * Try to bind again after timer {@link QTI_IMS_RETRY_TIMEOUT_MS} till valid binder
          * from QtiImsExtService.
          */
        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(LOG_TAG, "onNullBinding componentName " + name);
            unbindQtiImsExtService();
            mConnectionRetryHandler.sendEmptyMessageDelayed(QTI_IMS_EXT_SERVICE_CONNECT,
                    QTI_IMS_RETRY_TIMEOUT_MS);
        }
    };
}
