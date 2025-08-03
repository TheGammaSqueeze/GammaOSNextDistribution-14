/*
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

package com.google.android.car.kitchensink.media;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.service.media.IMediaBrowserService;
import android.service.media.IMediaBrowserServiceCallbacks;
import android.service.media.MediaBrowserService;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * A proxy to allow a connection from {@link android.media.browse.MediaBrowser} to {@link
 * android.service.media.MediaBrowserService} across user boundaries.
 *
 * <p>The {@code MediaBrowser} client connects to this service as if it is connecting to a
 * {@code MediaBrowserService}. It needs to pass the actual {@code MediaBrowserService}'s component
 * name as part the {@code rootHints} bundle, so that this proxy can know what the actual
 * {@code MediaBrowserService} is to connect to.
 */
public class MediaBrowserProxyService extends Service {

    private static final String TAG = MediaBrowserProxyService.class.getSimpleName();

    // The key to specify the actual media browser service in the rootHints bundle.
    public static final String MEDIA_BROWSER_SERVICE_COMPONENT_KEY =
            "media_browser_service_component_key";

    private HandlerThread mNativeHandlerThread;
    // Handler associated with the native worker thread.
    private Handler mNativeHandler;
    private Binder mBinder;

    private ComponentName mMediaBrowserServiceComponent;
    private IMediaBrowserService mServiceBinder;
    private MediaServiceConnection mServiceConnection;

    private final class MediaBrowserProxyServiceImpl extends IMediaBrowserService.Stub {

        private final Context mContext;

        MediaBrowserProxyServiceImpl(Context context) {
            mContext = context;
        }

        @Override
        public void connect(String pkg, Bundle rootHints, IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "connect() with pkg=" + pkg + ", rootHints=" + rootHints
                    + ", callbacks=" + callbacks
                    + ", callingUserId=" + Binder.getCallingUserHandle().getIdentifier()
                    + ", myUserId=" + UserHandle.myUserId());

            // The actual MediaBrowserService component is specified in the bundle.
            // Retrieve the info here and remove it from the bundle, and then pass the original
            // rootHints to the actual MediaBrowserService.
            String mediaBrowserServiceComponent =
                    rootHints.getString(MEDIA_BROWSER_SERVICE_COMPONENT_KEY);
            mMediaBrowserServiceComponent =
                    ComponentName.unflattenFromString(mediaBrowserServiceComponent);
            rootHints.remove(MEDIA_BROWSER_SERVICE_COMPONENT_KEY);
            Bundle origRootHints = rootHints.size() == 0 ? null : rootHints;

            mNativeHandler.post(() -> {
                Intent intent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
                intent.setComponent(mMediaBrowserServiceComponent);

                mServiceConnection = new MediaServiceConnection(pkg, origRootHints, callbacks);

                boolean bound = false;
                try {
                    bound = mContext.bindService(intent, mServiceConnection,
                            Context.BIND_AUTO_CREATE | Context.BIND_INCLUDE_CAPABILITIES);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed binding to service " + mMediaBrowserServiceComponent);
                }

                if (!bound) {
                    Log.d(TAG, "Cannot bind to MediaBrowserService: "
                            + mMediaBrowserServiceComponent);
                    mServiceConnection = null;
                    try {
                        callbacks.onConnectFailed();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed call onConnectionFailed");
                    }
                }
            });
        }

        @Override
        public void disconnect(IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "disconnect() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.disconnect(callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to disconnect MediaBrowserService", e);
                }
            });
        }

        @Override
        public void addSubscriptionDeprecated(String uri, IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "addSubscriptionDeprecated() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.addSubscriptionDeprecated(uri, callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to call MediaBrowserService#addSubscriptionDeprecated",
                            e);
                }
            });
        }

        @Override
        public void removeSubscriptionDeprecated(String uri,
                IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "removeSubscriptionDeprecated() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.removeSubscriptionDeprecated(uri, callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to call "
                            + " MediaBrowserService#removeSubscriptionDeprecated", e);
                }
            });
        }

        @Override
        public void getMediaItem(String uri, ResultReceiver cb,
                IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "getMediaItem() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.getMediaItem(uri, cb, callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to call MediaBrowserService#getMediaitem", e);
                }
            });
        }

        @Override
        public void addSubscription(String uri, IBinder token, Bundle options,
                IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "addSubscription() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.addSubscription(uri, token, options, callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to call MediaBrowserService#addSubscription", e);
                }
            });
        }

        @Override
        public void removeSubscription(String uri, IBinder token,
                IMediaBrowserServiceCallbacks callbacks) {
            Log.d(TAG, "removeSubscription() for user " + UserHandle.myUserId());
            mNativeHandler.post(() -> {
                try {
                    mServiceBinder.removeSubscription(uri, token, callbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to call MediaBrowserService#removeSubscription", e);
                }
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mNativeHandlerThread = new HandlerThread(MediaBrowserProxyService.class.getSimpleName());
        mNativeHandlerThread.start();
        mNativeHandler = new Handler(mNativeHandlerThread.getLooper());
        Log.d(TAG, "Started a native handler: " + mNativeHandler + " on thread; "
                + mNativeHandlerThread);

        mBinder = new MediaBrowserProxyServiceImpl(this);
    }

    @Override
    public void onDestroy() {
        mNativeHandlerThread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind() intent: " + intent);
        return mBinder;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.printf("mNativeHandlerThread: %s\n", mNativeHandlerThread);
        writer.printf("mNativeHandler: %s\n", mNativeHandler);
        writer.printf("mBinder: %s\n", mBinder);
        writer.printf("mMediaBrowserServiceComponent: %s\n", mMediaBrowserServiceComponent);
        writer.printf("mServiceBinder: %s\n", mServiceBinder);
        writer.printf("mServiceConnection: %s\n", mServiceConnection);
    }

    private final class MediaServiceConnection implements ServiceConnection {

        private final String mCallerPackage;
        private final Bundle mRootHints;
        private final IMediaBrowserServiceCallbacks mCallbacks;

        MediaServiceConnection(String pkg, Bundle rootHints,
                IMediaBrowserServiceCallbacks callbacks) {
            mCallerPackage = pkg;
            mRootHints = rootHints;
            mCallbacks = callbacks;
        }

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected(ComponentName: " + componentName.toShortString()
                    + ", Binder: " + iBinder + ")");

            mServiceBinder = IMediaBrowserService.Stub.asInterface(iBinder);

            // Connection from Proxy to MediaBrowserService has been established now.
            // Now calling MediaBrowserService#connect().
            mNativeHandler.post(() -> {
                Log.d(TAG, "Connecting MediaBrowserService " + mServiceBinder);
                try {
                    mServiceBinder.connect(mCallerPackage, mRootHints, mCallbacks);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect MediaBrowserBrowserService", e);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected(ComponentName: "
                    + componentName.toShortString() + ")");
            // No need to call mCallbacks.onConnectFailed() here, because we passed the callbacks
            // to the actual MediaBrowserService, and the call back will be invoked by it.
            mServiceBinder = null;
        }
    }
}
