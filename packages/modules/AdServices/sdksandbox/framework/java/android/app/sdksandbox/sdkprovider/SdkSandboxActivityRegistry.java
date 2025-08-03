/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.sdksandbox.sdkprovider;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SANDBOXED_ACTIVITY_HANDLER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.sandboxactivity.ActivityContextInfo;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.ArrayMap;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;

import java.util.Iterator;
import java.util.Map;

/**
 * It is a Singleton class to store the registered {@link SdkSandboxActivityHandler} instances and
 * their associated {@link Activity} instances.
 *
 * @hide
 */
public class SdkSandboxActivityRegistry {
    private static final String TAG = "SdkSandboxActivityRegistry";

    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static SdkSandboxActivityRegistry sInstance;

    // A lock to keep all map synchronized
    private final Object mMapsLock = new Object();

    @GuardedBy("mMapsLock")
    private final Map<SdkSandboxActivityHandler, HandlerInfo> mHandlerToHandlerInfoMap =
            new ArrayMap<>();

    @GuardedBy("mMapsLock")
    private final Map<IBinder, HandlerInfo> mTokenToHandlerInfoMap = new ArrayMap<>();

    private SdkSandboxActivityRegistry() {}

    /** Returns a singleton instance of this class. */
    public static SdkSandboxActivityRegistry getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new SdkSandboxActivityRegistry();
            }
            return sInstance;
        }
    }

    /**
     * Registers the passed {@link SdkSandboxActivityHandler} and returns a {@link IBinder} token
     * that identifies it.
     *
     * <p>If {@link SdkSandboxActivityHandler} is already registered, its {@link IBinder} identifier
     * will be returned.
     *
     * @param sdkContext is the {@link SandboxedSdkContext} which is registering the {@link
     *     SdkSandboxActivityHandler}
     * @param handler is the {@link SdkSandboxActivityHandler} to register.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public IBinder register(
            @NonNull SandboxedSdkContext sdkContext, @NonNull SdkSandboxActivityHandler handler) {
        synchronized (mMapsLock) {
            if (mHandlerToHandlerInfoMap.containsKey(handler)) {
                HandlerInfo handlerInfo = mHandlerToHandlerInfoMap.get(handler);
                return handlerInfo.getToken();
            }

            IBinder token = new Binder();
            HandlerInfo handlerInfo = new HandlerInfo(sdkContext, handler, token);
            mHandlerToHandlerInfoMap.put(handlerInfo.getHandler(), handlerInfo);
            mTokenToHandlerInfoMap.put(handlerInfo.getToken(), handlerInfo);
            return token;
        }
    }

    /**
     * Unregisters the passed {@link SdkSandboxActivityHandler}.
     *
     * @param handler is the {@link SdkSandboxActivityHandler} to unregister.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void unregister(@NonNull SdkSandboxActivityHandler handler) {
        synchronized (mMapsLock) {
            HandlerInfo handlerInfo = mHandlerToHandlerInfoMap.get(handler);
            if (handlerInfo == null) {
                return;
            }
            mHandlerToHandlerInfoMap.remove(handlerInfo.getHandler());
            mTokenToHandlerInfoMap.remove(handlerInfo.getToken());
        }
    }

    /**
     * Notifies the SDK about {@link Activity} creation.
     *
     * <p>This should be called by the sandbox {@link Activity} while being created to notify the
     * SDK that registered the {@link SdkSandboxActivityHandler} that identified by an {@link
     * IBinder} token which be part of the passed {@link Intent} extras.
     *
     * @param intent the {@link Intent} that contains an {@link IBinder} identifier for the {@link
     *     SdkSandboxActivityHandler} in its extras.
     * @param activity the {@link Activity} is being created.
     * @throws IllegalArgumentException if the passed {@link Intent} does not have the handler token
     *     in its extras or there is no registered handler for the passed handler token (that mostly
     *     would mean that the handler is de-registered before the passed {@link Activity} is
     *     created), on both cases the passed {@link Activity} will not start.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void notifyOnActivityCreation(@NonNull Intent intent, @NonNull Activity activity) {
        synchronized (mMapsLock) {
            IBinder handlerToken = extractHandlerToken(intent);
            if (handlerToken == null) {
                throw new IllegalArgumentException(
                        "Extra params of the intent are missing the IBinder value for the key ("
                                + SdkSandboxManager.EXTRA_SANDBOXED_ACTIVITY_HANDLER
                                + ")");
            }
            HandlerInfo handlerInfo = mTokenToHandlerInfoMap.get(handlerToken);
            if (handlerInfo == null) {
                throw new IllegalArgumentException(
                        "There is no registered SdkSandboxActivityHandler to notify");
            }
            handlerInfo.getHandler().onActivityCreated(activity);
        }
    }

    /**
     * Returns {@link ActivityContextInfo} instance containing the information which is needed to
     * build the sandbox activity {@link android.content.Context} for the passed {@link Intent}.
     *
     * @param intent an {@link Intent} for a sandbox {@link Activity} containing information to
     *     identify the SDK which requested the activity.
     * @return {@link ActivityContextInfo} instance if the intent refers to a registered {@link
     *     SdkSandboxActivityHandler}, otherwise {@code null}.
     * @throws IllegalStateException if Customized SDK Context flag is not enabled
     */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public ActivityContextInfo getContextInfo(@NonNull Intent intent) {
        synchronized (mMapsLock) {
            final IBinder handlerToken = extractHandlerToken(intent);
            if (handlerToken == null) {
                return null;
            }
            final HandlerInfo handlerInfo = mTokenToHandlerInfoMap.get(handlerToken);
            if (handlerInfo == null) {
                return null;
            }
            if (!handlerInfo.getSdkContext().isCustomizedSdkContextEnabled()) {
                throw new IllegalStateException("Customized SDK flag is disabled.");
            }
            return handlerInfo.getContextInfo();
        }
    }

    /**
     * Returns the SDK {@link SandboxedSdkContext} which requested the activity for the passed
     * {@link Intent}.
     *
     * @param intent the {@link Intent} that contains an {@link IBinder} identifier for the {@link
     *     SdkSandboxActivityHandler} in its extras.
     * @return SDK {@link SandboxedSdkContext} if its handler is registered, otherwise {@code null}`
     *     .
     */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public SandboxedSdkContext getSdkContext(@NonNull Intent intent) {
        synchronized (mMapsLock) {
            final IBinder handlerToken = extractHandlerToken(intent);
            if (handlerToken == null) {
                return null;
            }
            final HandlerInfo handlerInfo = mTokenToHandlerInfoMap.get(handlerToken);
            if (handlerInfo == null) {
                return null;
            }
            return handlerInfo.getSdkContext();
        }
    }

    @Nullable
    private IBinder extractHandlerToken(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return null;
        }
        return intent.getExtras().getBinder(EXTRA_SANDBOXED_ACTIVITY_HANDLER);
    }

    /**
     * Unregisters all {@link SdkSandboxActivityHandler} instances that are registered by the passed
     * SDK.
     *
     * <p>This is expected to be called by the system when an SDK is unloaded to free memory.
     *
     * @param sdkName the name of the SDK to unregister its registered handlers
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void unregisterAllActivityHandlersForSdk(@NonNull String sdkName) {
        synchronized (mMapsLock) {
            Iterator<Map.Entry<SdkSandboxActivityHandler, HandlerInfo>> iter =
                    mHandlerToHandlerInfoMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<SdkSandboxActivityHandler, HandlerInfo> handlerEntry = iter.next();
                HandlerInfo handlerInfo = handlerEntry.getValue();
                if (handlerInfo.getSdkContext().getSdkName().equals(sdkName)) {
                    IBinder handlerToken = handlerInfo.getToken();
                    iter.remove();
                    mTokenToHandlerInfoMap.remove(handlerToken);
                }
            }
        }
    }

    /**
     * Holds the information about {@link SdkSandboxActivityHandler}.
     *
     * @hide
     */
    private static class HandlerInfo {
        private final SandboxedSdkContext mSdkContext;
        private final SdkSandboxActivityHandler mHandler;
        private final IBinder mToken;
        private final ActivityContextInfo mContextInfo;

        HandlerInfo(
                SandboxedSdkContext sdkContext, SdkSandboxActivityHandler handler, IBinder token) {
            this.mSdkContext = sdkContext;
            this.mHandler = handler;
            this.mToken = token;
            mContextInfo = mSdkContext::getApplicationInfo;
        }

        @NonNull
        public SandboxedSdkContext getSdkContext() {
            return mSdkContext;
        }

        @NonNull
        public SdkSandboxActivityHandler getHandler() {
            return mHandler;
        }

        @NonNull
        public IBinder getToken() {
            return mToken;
        }

        @NonNull
        public ActivityContextInfo getContextInfo() {
            return mContextInfo;
        }
    }
}
