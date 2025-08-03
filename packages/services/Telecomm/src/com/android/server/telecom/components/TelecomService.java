/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom.components;

import android.app.Service;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.media.IAudioService;
import android.media.ToneGenerator;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.BlockedNumberContract;
import android.telecom.Log;

import android.telecom.CallerInfoAsyncQuery;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.telecom.IInternalServiceRetriever;
import com.android.internal.telecom.ITelecomLoader;
import com.android.internal.telecom.ITelecomService;
import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.CallAudioModeStateMachine;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallerInfoAsyncQueryFactory;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.ConnectionServiceFocusManager;
import com.android.server.telecom.ContactsAsyncHelper;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.DeviceIdleControllerAdapter;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.HeadsetMediaButtonFactory;
import com.android.server.telecom.InCallWakeLockControllerFactory;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.InternalServiceRetrieverAdapter;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneNumberUtilsAdapterImpl;
import com.android.server.telecom.ProximitySensorManagerFactory;
import com.android.server.telecom.InCallWakeLockController;
import com.android.server.telecom.ProximitySensorManager;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.RoleManagerAdapterImpl;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.TelecomWakeLock;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.callfiltering.BlockedNumbersAdapter;
import com.android.server.telecom.flags.FeatureFlagsImpl;
import com.android.server.telecom.settings.BlockedNumbersUtil;
import com.android.server.telecom.ui.IncomingCallNotifier;
import com.android.server.telecom.ui.MissedCallNotifierImpl;
import com.android.server.telecom.ui.NotificationChannelManager;

import java.util.concurrent.Executors;

/**
 * Implementation of the ITelecom interface.
 */
public class TelecomService extends Service implements TelecomSystem.Component {

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this, "onBind");
        return new ITelecomLoader.Stub() {
            @Override
            public ITelecomService createTelecomService(IInternalServiceRetriever retriever) {
                InternalServiceRetrieverAdapter adapter =
                        new InternalServiceRetrieverAdapter(retriever);
                initializeTelecomSystem(TelecomService.this, adapter);
                synchronized (getTelecomSystem().getLock()) {
                    return getTelecomSystem().getTelecomServiceImpl().getBinder();
                }
            }
        };
    }

    /**
     * This method is to be called by components (Activitys, Services, ...) to initialize the
     * Telecom singleton. It should only be called on the main thread. As such, it is atomic
     * and needs no synchronization -- it will either perform its initialization, after which
     * the {@link TelecomSystem#getInstance()} will be initialized, or some other invocation of
     * this method on the main thread will have happened strictly prior to it, and this method
     * will be a benign no-op.
     *
     * @param context
     */
    static void initializeTelecomSystem(Context context,
            InternalServiceRetrieverAdapter internalServiceRetriever) {
        if (TelecomSystem.getInstance() == null) {
            NotificationChannelManager notificationChannelManager =
                    new NotificationChannelManager();
            notificationChannelManager.createChannels(context);

            TelecomSystem.setInstance(
                    new TelecomSystem(
                            context,
                            new MissedCallNotifierImpl.MissedCallNotifierImplFactory() {
                                @Override
                                public MissedCallNotifierImpl makeMissedCallNotifierImpl(
                                        Context context,
                                        PhoneAccountRegistrar phoneAccountRegistrar,
                                        DefaultDialerCache defaultDialerCache,
                                        DeviceIdleControllerAdapter idleControllerAdapter,
                                        FeatureFlags featureFlags) {
                                    return new MissedCallNotifierImpl(context,
                                            phoneAccountRegistrar, defaultDialerCache,
                                            idleControllerAdapter, featureFlags);
                                }
                            },
                            new CallerInfoAsyncQueryFactory() {
                                @Override
                                public CallerInfoAsyncQuery startQuery(
                                        int token,
                                        Context context,
                                        String number,
                                        CallerInfoAsyncQuery.OnQueryCompleteListener listener,
                                        Object cookie) {
                                    Log.i(TelecomSystem.getInstance(),
                                            "CallerInfoAsyncQuery.startQuery number=%s cookie=%s",
                                            Log.pii(number), cookie);
                                    return CallerInfoAsyncQuery.startQuery(
                                            token, context, number, listener, cookie);
                                }
                            },
                            new HeadsetMediaButtonFactory() {
                                @Override
                                public HeadsetMediaButton create(
                                        Context context,
                                        CallsManager callsManager,
                                        TelecomSystem.SyncRoot lock) {
                                    return new HeadsetMediaButton(context, callsManager, lock);
                                }
                            },
                            new ProximitySensorManagerFactory() {
                                @Override
                                public ProximitySensorManager create(
                                        Context context,
                                        CallsManager callsManager) {
                                    return new ProximitySensorManager(
                                            new TelecomWakeLock(
                                                    context,
                                                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                                                    ProximitySensorManager.class.getSimpleName()),
                                            callsManager);
                                }
                            },
                            new InCallWakeLockControllerFactory() {
                                @Override
                                public InCallWakeLockController create(Context context,
                                        CallsManager callsManager) {
                                    return new InCallWakeLockController(
                                            new TelecomWakeLock(context,
                                                    PowerManager.FULL_WAKE_LOCK,
                                                    InCallWakeLockController.class.getSimpleName()),
                                            callsManager);
                                }
                            },
                            new CallAudioManager.AudioServiceFactory() {
                                @Override
                                public IAudioService getAudioService() {
                                    return IAudioService.Stub.asInterface(
                                            ServiceManager.getService(Context.AUDIO_SERVICE));
                                }
                            },
                            ConnectionServiceFocusManager::new,
                            new Timeouts.Adapter(),
                            new AsyncRingtonePlayer(),
                            new PhoneNumberUtilsAdapterImpl(),
                            new IncomingCallNotifier(context),
                            ToneGenerator::new,
                            new CallAudioRouteStateMachine.Factory(),
                            new CallAudioModeStateMachine.Factory(),
                            new ClockProxy() {
                                @Override
                                public long currentTimeMillis() {
                                    return System.currentTimeMillis();
                                }

                                @Override
                                public long elapsedRealtime() {
                                    return SystemClock.elapsedRealtime();
                                }
                            },
                            new RoleManagerAdapterImpl(context,
                                    (RoleManager) context.getSystemService(Context.ROLE_SERVICE)),
                            new ContactsAsyncHelper.Factory(),
                            internalServiceRetriever.getDeviceIdleController(),
                            new Ringer.AccessibilityManagerAdapter() {
                                @Override
                                public boolean startFlashNotificationSequence(
                                        @androidx.annotation.NonNull Context context, int reason) {
                                    return context.getSystemService(AccessibilityManager.class)
                                            .startFlashNotificationSequence(context, reason);
                                }

                                @Override
                                public boolean stopFlashNotificationSequence(
                                        @androidx.annotation.NonNull Context context) {
                                    return context.getSystemService(AccessibilityManager.class)
                                            .stopFlashNotificationSequence(context);
                                }
                            },
                            Executors.newCachedThreadPool(),
                            Executors.newSingleThreadExecutor(),
                            new BlockedNumbersAdapter() {
                                @Override
                                public boolean shouldShowEmergencyCallNotification(Context
                                        context) {
                                    return BlockedNumberContract.BlockedNumbers
                                            .shouldShowEmergencyCallNotification(context);
                                }

                                @Override
                                public void updateEmergencyCallNotification(Context context,
                                        boolean showNotification) {
                                    BlockedNumbersUtil.updateEmergencyCallNotification(context,
                                            showNotification);
                                }
                            },
                            new FeatureFlagsImpl(),
                            new com.android.internal.telephony.flags.FeatureFlagsImpl()));
        }
    }

    @Override
    public TelecomSystem getTelecomSystem() {
        return TelecomSystem.getInstance();
    }
}
