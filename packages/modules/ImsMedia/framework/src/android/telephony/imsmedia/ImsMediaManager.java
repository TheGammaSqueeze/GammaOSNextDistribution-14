/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony.imsmedia;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.net.DatagramSocket;
import java.util.Objects;
import java.util.concurrent.Executor;


/**
 * IMS media manager APIs to manage the IMS media sessions
 *
 * @hide
 */
public class ImsMediaManager {
    private static final String TAG = "ImsMediaManager";
    @VisibleForTesting
    protected static final String MEDIA_SERVICE_PACKAGE = "com.android.telephony.imsmedia";
    @VisibleForTesting
    protected static final String MEDIA_SERVICE_CLASS =
            MEDIA_SERVICE_PACKAGE + ".ImsMediaController";
    private final Context mContext;
    private final OnConnectedCallback mOnConnectedCallback;
    private final Executor mExecutor;
    private final ServiceConnection mConnection;
    private volatile IImsMedia mImsMedia;

    /**
     * Opens a RTP session based on the local sockets with the associated initial
     * remote configuration if there is a valid {@link RtpConfig} passed. It starts
     * the media flow if the media direction in the {@link RtpConfig} is set to any
     * value other than {@link RtpConfig#NO_MEDIA_FLOW}. If the open session is
     * successful then a new {@link ImsMediaSession} object will be returned using
     * the {@link SessionCallback#onOpenSessionSuccess(ImsMediaSession)} API. If the
     * open session is failed then an error code {@link SessionOperationResult} will
     * be returned using {@link SessionCallback#onOpenSessionFailure(int)} API.
     *
     * @param rtpSocket  local UDP socket to send and receive incoming RTP packets
     * @param rtcpSocket local UDP socket to send and receive incoming RTCP packets
     * @param rtpConfig  provides remote endpoint info and codec details.
     *                   This could be null initially and the application may update
     *                   this later using {@link ImsMediaSession#modifySession()}
     *                   API.
     * @param callback   callbacks to receive session specific notifications.
     */
    public void openSession(@NonNull final DatagramSocket rtpSocket,
            @NonNull final DatagramSocket rtcpSocket,
            @NonNull final @ImsMediaSession.SessionType int sessionType,
            @Nullable final RtpConfig rtpConfig,
            @NonNull final Executor executor,
            @NonNull final SessionCallback callback) {
        if (isConnected()) {
            try {
                callback.setExecutor(executor);
                mImsMedia.openSession(ParcelFileDescriptor.fromDatagramSocket(rtpSocket),
                        ParcelFileDescriptor.fromDatagramSocket(rtcpSocket), sessionType,
                        rtpConfig, callback.getBinder());
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to openSession: " + e);
                // TODO throw exception or callback.onOpenSessionFailure()
            }
        }
    }

    /**
     * Closes the RTP session including cleanup of all the resources
     * associated with the session. This will also close the session object
     * and associated callback.
     *
     * @param session RTP session to be closed.
     */
    public void closeSession(@NonNull ImsMediaSession session) {
        if (isConnected()) {
            try {
                mImsMedia.closeSession(session.getBinder());
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to closeSession: " + e);
            }
        }
    }

    /**
     * Generates the array of SPROP strings for the given array of video
     * configurations and returns via IImsMediaCallback.
     *
     * @param videoConfigList array of video configuration for which sprop should be generated.
     * @param callback Binder interface implemented by caller and called with array of generated
     * sprop values.
     **/
    public void generateVideoSprop(@NonNull VideoConfig[] videoConfigList, IBinder callback) {
        if (isConnected()) {
            try {
                mImsMedia.generateVideoSprop(videoConfigList, callback);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to closeSession: " + e);
            }
        }
    }

    /**
     * Unbinds the service connection with ImsMediaService
     */
    public void release() {
        // TODO: close all the open sessions
        if (isConnected()) {
            try {
                mContext.unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "IllegalArgumentException: " + e.toString());
            }
            mImsMedia = null;
        }
    }

    /**
     * Interface to send call-backs to the application when the service is
     * connected.
     */
    public interface OnConnectedCallback {
        /**
         * Called by the ImsMedia framework when the service is connected.
         */
        void onConnected();

        /**
         * Called by the ImsMedia framework when the service is disconnected.
         */
        void onDisconnected();
    }

    public ImsMediaManager(@NonNull Context context, @NonNull Executor executor,
            @NonNull OnConnectedCallback callback) {

        mContext = Objects.requireNonNull(context, "context cannot be null");
        mExecutor = Objects.requireNonNull(executor, "executor cannot be null");
        mOnConnectedCallback = Objects.requireNonNull(callback, "callback cannot be null");

        mConnection = new ServiceConnection() {

            public synchronized void onServiceConnected(
                    ComponentName className, IBinder service) {

                mImsMedia = IImsMedia.Stub.asInterface(service);
                Log.d(TAG, "onServiceConnected");
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mOnConnectedCallback.onConnected();
                    }
                });
            }

            public void onServiceDisconnected(ComponentName className) {
                Log.d(TAG, "onServiceDisconnected");
                mImsMedia = null;
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mOnConnectedCallback.onDisconnected();
                    }
                });
            }
        };

        Intent intent = new Intent(IImsMedia.class.getName());
        intent.setClassName(MEDIA_SERVICE_PACKAGE, MEDIA_SERVICE_CLASS);
        boolean bindingSuccessful = mContext.bindService(intent, mConnection,
                Context.BIND_AUTO_CREATE);

        Log.d(TAG, "binding: " + bindingSuccessful);
        if (bindingSuccessful) {
            Log.d(TAG, "bindService successful");
        }
    }

    private boolean isConnected() {
        return mImsMedia != null;
    }

    public static abstract class SessionCallback {
        /** @hide */
        public IBinder getBinder() {
            // Base Implementation
            return null;
        }

        /** @hide */
        public void setExecutor(Executor executor) {
            // Base Implementation
        }

        /**
         * Called when the session is opened successfully
         *
         * @param session session object
         */
        public void onOpenSessionSuccess(ImsMediaSession session) {
            // Base Implementation
        }

        /**
         * Called when the open session fails
         *
         * @param error Error code
         */
        public void onOpenSessionFailure(int error) {
            // Base Implementation
        }

        /**
         * Called when the session is closed.
         */
        public void onSessionClosed() {
            // Base Implementation
        }

    }
}
