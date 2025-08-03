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

package com.android.server.telecom;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BugreportManager;
import android.os.DropBoxManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;
import android.telephony.AnomalyReporter;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.CallAudioManager.AudioServiceFactory;
import com.android.server.telecom.DefaultDialerCache.DefaultDialerManagerAdapter;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;
import com.android.server.telecom.callfiltering.BlockedNumbersAdapter;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.callfiltering.IncomingCallFilterGraph;
import com.android.server.telecom.callfiltering.IncomingCallFilterGraphProvider;
import com.android.server.telecom.components.UserCallIntentProcessor;
import com.android.server.telecom.components.UserCallIntentProcessorFactory;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.ui.AudioProcessingNotification;
import com.android.server.telecom.ui.CallStreamingNotification;
import com.android.server.telecom.ui.DisconnectedCallNotifier;
import com.android.server.telecom.ui.IncomingCallNotifier;
import com.android.server.telecom.ui.MissedCallNotifierImpl.MissedCallNotifierImplFactory;
import com.android.server.telecom.ui.ToastFactory;
import com.android.server.telecom.voip.TransactionManager;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Top-level Application class for Telecom.
 */
public class TelecomSystem {

    /**
     * This interface is implemented by system-instantiated components (e.g., Services and
     * Activity-s) that wish to use the TelecomSystem but would like to be testable. Such a
     * component should implement the getTelecomSystem() method to return the global singleton,
     * and use its own method. Tests can subclass the component to return a non-singleton.
     *
     * A refactoring goal for Telecom is to limit use of the TelecomSystem singleton to those
     * system-instantiated components, and have all other parts of the system just take all their
     * dependencies as explicit arguments to their constructor or other methods.
     */
    public interface Component {
        TelecomSystem getTelecomSystem();
    }


    /**
     * Tagging interface for the object used for synchronizing multi-threaded operations in
     * the Telecom system.
     */
    public interface SyncRoot {
    }

    private static final IntentFilter USER_SWITCHED_FILTER =
            new IntentFilter(Intent.ACTION_USER_SWITCHED);

    private static final IntentFilter USER_STARTING_FILTER =
            new IntentFilter(Intent.ACTION_USER_STARTING);

    private static final IntentFilter BOOT_COMPLETE_FILTER =
            new IntentFilter(Intent.ACTION_BOOT_COMPLETED);

    /** Intent filter for dialer secret codes. */
    private static final IntentFilter DIALER_SECRET_CODE_FILTER;

    /**
     * Initializes the dialer secret code intent filter.  Setup to handle the various secret codes
     * which can be dialed (e.g. in format *#*#code#*#*) to trigger various behavior in Telecom.
     */
    static {
        DIALER_SECRET_CODE_FILTER = new IntentFilter(
                "android.provider.Telephony.SECRET_CODE");
        DIALER_SECRET_CODE_FILTER.addDataScheme("android_secret_code");
        DIALER_SECRET_CODE_FILTER
                .addDataAuthority(DialerCodeReceiver.TELECOM_SECRET_CODE_DEBUG_ON, null);
        DIALER_SECRET_CODE_FILTER
                .addDataAuthority(DialerCodeReceiver.TELECOM_SECRET_CODE_DEBUG_OFF, null);
        DIALER_SECRET_CODE_FILTER
                .addDataAuthority(DialerCodeReceiver.TELECOM_SECRET_CODE_MARK, null);
        DIALER_SECRET_CODE_FILTER
                .addDataAuthority(DialerCodeReceiver.TELECOM_SECRET_CODE_MENU, null);

        USER_SWITCHED_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        USER_STARTING_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        BOOT_COMPLETE_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        DIALER_SECRET_CODE_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
    }

    private static TelecomSystem INSTANCE = null;

    private final SyncRoot mLock = new SyncRoot() { };
    private final MissedCallNotifier mMissedCallNotifier;
    private final IncomingCallNotifier mIncomingCallNotifier;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final CallsManager mCallsManager;
    private final RespondViaSmsManager mRespondViaSmsManager;
    private final Context mContext;
    private final CallIntentProcessor mCallIntentProcessor;
    private final TelecomBroadcastIntentProcessor mTelecomBroadcastIntentProcessor;
    private final TelecomServiceImpl mTelecomServiceImpl;
    private final ContactsAsyncHelper mContactsAsyncHelper;
    private final DialerCodeReceiver mDialerCodeReceiver;
    private final FeatureFlags mFeatureFlags;

    private boolean mIsBootComplete = false;

    private final BroadcastReceiver mUserSwitchedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("TSSwR.oR");
            try {
                synchronized (mLock) {
                    int userHandleId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                    UserHandle currentUserHandle = new UserHandle(userHandleId);
                    mPhoneAccountRegistrar.setCurrentUserHandle(currentUserHandle);
                    mCallsManager.onUserSwitch(currentUserHandle);
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private final BroadcastReceiver mUserStartingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("TSStR.oR");
            try {
                synchronized (mLock) {
                    int userHandleId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
                    UserHandle addingUserHandle = new UserHandle(userHandleId);
                    mCallsManager.onUserStarting(addingUserHandle);
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private final BroadcastReceiver mBootCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("TSBCR.oR");
            try {
                synchronized (mLock) {
                    mIsBootComplete = true;
                    mCallsManager.onBootCompleted();
                }
            } finally {
                Log.endSession();
            }
        }
    };

    public static TelecomSystem getInstance() {
        return INSTANCE;
    }

    public static void setInstance(TelecomSystem instance) {
        if (INSTANCE != null) {
            Log.w("TelecomSystem", "Attempt to set TelecomSystem.INSTANCE twice");
        }
        Log.i(TelecomSystem.class, "TelecomSystem.INSTANCE being set");
        INSTANCE = instance;
    }

    public TelecomSystem(
            Context context,
            MissedCallNotifierImplFactory missedCallNotifierImplFactory,
            CallerInfoAsyncQueryFactory callerInfoAsyncQueryFactory,
            HeadsetMediaButtonFactory headsetMediaButtonFactory,
            ProximitySensorManagerFactory proximitySensorManagerFactory,
            InCallWakeLockControllerFactory inCallWakeLockControllerFactory,
            AudioServiceFactory audioServiceFactory,
            ConnectionServiceFocusManager.ConnectionServiceFocusManagerFactory
                    connectionServiceFocusManagerFactory,
            Timeouts.Adapter timeoutsAdapter,
            AsyncRingtonePlayer asyncRingtonePlayer,
            PhoneNumberUtilsAdapter phoneNumberUtilsAdapter,
            IncomingCallNotifier incomingCallNotifier,
            InCallTonePlayer.ToneGeneratorFactory toneGeneratorFactory,
            CallAudioRouteStateMachine.Factory callAudioRouteStateMachineFactory,
            CallAudioModeStateMachine.Factory callAudioModeStateMachineFactory,
            ClockProxy clockProxy,
            RoleManagerAdapter roleManagerAdapter,
            ContactsAsyncHelper.Factory contactsAsyncHelperFactory,
            DeviceIdleControllerAdapter deviceIdleControllerAdapter,
            Ringer.AccessibilityManagerAdapter accessibilityManagerAdapter,
            Executor asyncTaskExecutor,
            Executor asyncCallAudioTaskExecutor,
            BlockedNumbersAdapter blockedNumbersAdapter,
            FeatureFlags featureFlags,
            com.android.internal.telephony.flags.FeatureFlags telephonyFlags) {
        mContext = context.getApplicationContext();
        mFeatureFlags = featureFlags;
        LogUtils.initLogging(mContext);
        android.telecom.Log.setLock(mLock);
        AnomalyReporter.initialize(mContext);
        DefaultDialerManagerAdapter defaultDialerAdapter =
                new DefaultDialerCache.DefaultDialerManagerAdapterImpl();

        DefaultDialerCache defaultDialerCache = new DefaultDialerCache(mContext,
                defaultDialerAdapter, roleManagerAdapter, mLock);

        Log.startSession("TS.init");
        // Wrap this in a try block to ensure session cleanup occurs in the case of error.
        try {
            mPhoneAccountRegistrar = new PhoneAccountRegistrar(mContext, mLock, defaultDialerCache,
                    packageName -> AppLabelProxy.Util.getAppLabel(
                            mContext.getPackageManager(), packageName), null, mFeatureFlags);

            mContactsAsyncHelper = contactsAsyncHelperFactory.create(
                    new ContactsAsyncHelper.ContentResolverAdapter() {
                        @Override
                        public InputStream openInputStream(Context context, Uri uri)
                                throws FileNotFoundException {
                            return context.getContentResolver().openInputStream(uri);
                        }
                    });
            CallAudioCommunicationDeviceTracker communicationDeviceTracker = new
                    CallAudioCommunicationDeviceTracker(mContext);
            BluetoothDeviceManager bluetoothDeviceManager = new BluetoothDeviceManager(mContext,
                    mContext.getSystemService(BluetoothManager.class).getAdapter(),
                    communicationDeviceTracker, featureFlags);
            BluetoothRouteManager bluetoothRouteManager = new BluetoothRouteManager(mContext, mLock,
                    bluetoothDeviceManager, new Timeouts.Adapter(),
                    communicationDeviceTracker, featureFlags);
            BluetoothStateReceiver bluetoothStateReceiver = new BluetoothStateReceiver(
                    bluetoothDeviceManager, bluetoothRouteManager,
                    communicationDeviceTracker, featureFlags);
            mContext.registerReceiver(bluetoothStateReceiver, BluetoothStateReceiver.INTENT_FILTER);
            communicationDeviceTracker.setBluetoothRouteManager(bluetoothRouteManager);

            WiredHeadsetManager wiredHeadsetManager = new WiredHeadsetManager(mContext);
            SystemStateHelper systemStateHelper = new SystemStateHelper(mContext, mLock);

            mMissedCallNotifier = missedCallNotifierImplFactory
                    .makeMissedCallNotifierImpl(mContext, mPhoneAccountRegistrar,
                            defaultDialerCache,
                            deviceIdleControllerAdapter,
                            featureFlags);
            DisconnectedCallNotifier.Factory disconnectedCallNotifierFactory =
                    new DisconnectedCallNotifier.Default();

            CallerInfoLookupHelper callerInfoLookupHelper =
                    new CallerInfoLookupHelper(context, callerInfoAsyncQueryFactory,
                            mContactsAsyncHelper, mLock);

            EmergencyCallHelper emergencyCallHelper = new EmergencyCallHelper(mContext,
                    defaultDialerCache, timeoutsAdapter);

            InCallControllerFactory inCallControllerFactory = new InCallControllerFactory() {
                @Override
                public InCallController create(Context context, SyncRoot lock,
                        CallsManager callsManager, SystemStateHelper systemStateProvider,
                        DefaultDialerCache defaultDialerCache, Timeouts.Adapter timeoutsAdapter,
                        EmergencyCallHelper emergencyCallHelper) {
                    return new InCallController(context, lock, callsManager, systemStateProvider,
                            defaultDialerCache, timeoutsAdapter, emergencyCallHelper,
                            new CarModeTracker(), clockProxy, featureFlags);
                }
            };

            CallEndpointControllerFactory callEndpointControllerFactory =
                    new CallEndpointControllerFactory() {
                @Override
                public CallEndpointController create(Context context, SyncRoot lock,
                        CallsManager callsManager) {
                    return new CallEndpointController(context, callsManager);
                }
            };

            CallDiagnosticServiceController callDiagnosticServiceController =
                    new CallDiagnosticServiceController(
                            new CallDiagnosticServiceController.ContextProxy() {
                                @Override
                                public List<ResolveInfo> queryIntentServicesAsUser(
                                        @NonNull Intent intent, int flags, int userId) {
                                    return mContext.getPackageManager().queryIntentServicesAsUser(
                                            intent, flags, userId);
                                }

                                @Override
                                public boolean bindServiceAsUser(@NonNull Intent service,
                                        @NonNull ServiceConnection conn, int flags,
                                        @NonNull UserHandle user) {
                                    return mContext.bindServiceAsUser(service, conn, flags, user);
                                }

                                @Override
                                public void unbindService(@NonNull ServiceConnection conn) {
                                    mContext.unbindService(conn);
                                }

                                @Override
                                public UserHandle getCurrentUserHandle() {
                                    return mCallsManager.getCurrentUserHandle();
                                }
                            },
                            mContext.getResources().getString(
                                    com.android.server.telecom.R.string
                                            .call_diagnostic_service_package_name),
                            mLock
                    );

            AudioProcessingNotification audioProcessingNotification =
                    new AudioProcessingNotification(mContext);

            ToastFactory toastFactory = new ToastFactory() {
                @Override
                public Toast makeText(Context context, int resId, int duration) {
                    if (mFeatureFlags.telecomResolveHiddenDependencies()) {
                        return Toast.makeText(context, resId, duration);
                    } else {
                        return Toast.makeText(context, context.getMainLooper(),
                                context.getString(resId),
                                duration);
                    }
                }

                @Override
                public Toast makeText(Context context, CharSequence text, int duration) {
                    if (mFeatureFlags.telecomResolveHiddenDependencies()) {
                        return Toast.makeText(context, text, duration);
                    } else {
                        return Toast.makeText(context, context.getMainLooper(), text, duration);
                    }
                }
            };

            EmergencyCallDiagnosticLogger emergencyCallDiagnosticLogger =
                    new EmergencyCallDiagnosticLogger(mContext.getSystemService(
                            TelephonyManager.class), mContext.getSystemService(
                            BugreportManager.class), timeoutsAdapter, mContext.getSystemService(
                            DropBoxManager.class), asyncTaskExecutor, clockProxy);

            CallAnomalyWatchdog callAnomalyWatchdog = new CallAnomalyWatchdog(
                    Executors.newSingleThreadScheduledExecutor(),
                    mLock, timeoutsAdapter, clockProxy, emergencyCallDiagnosticLogger);

            TransactionManager transactionManager = TransactionManager.getInstance();

            CallStreamingNotification callStreamingNotification =
                    new CallStreamingNotification(mContext,
                            packageName -> AppLabelProxy.Util.getAppLabel(
                                    mContext.getPackageManager(), packageName), asyncTaskExecutor);

            mCallsManager = new CallsManager(
                    mContext,
                    mLock,
                    callerInfoLookupHelper,
                    mMissedCallNotifier,
                    disconnectedCallNotifierFactory,
                    mPhoneAccountRegistrar,
                    headsetMediaButtonFactory,
                    proximitySensorManagerFactory,
                    inCallWakeLockControllerFactory,
                    connectionServiceFocusManagerFactory,
                    audioServiceFactory,
                    bluetoothRouteManager,
                    wiredHeadsetManager,
                    systemStateHelper,
                    defaultDialerCache,
                    timeoutsAdapter,
                    asyncRingtonePlayer,
                    phoneNumberUtilsAdapter,
                    emergencyCallHelper,
                    toneGeneratorFactory,
                    clockProxy,
                    audioProcessingNotification,
                    bluetoothStateReceiver,
                    callAudioRouteStateMachineFactory,
                    callAudioModeStateMachineFactory,
                    inCallControllerFactory,
                    callDiagnosticServiceController,
                    roleManagerAdapter,
                    toastFactory,
                    callEndpointControllerFactory,
                    callAnomalyWatchdog,
                    accessibilityManagerAdapter,
                    asyncTaskExecutor,
                    asyncCallAudioTaskExecutor,
                    blockedNumbersAdapter,
                    transactionManager,
                    emergencyCallDiagnosticLogger,
                    communicationDeviceTracker,
                    callStreamingNotification,
                    bluetoothDeviceManager,
                    featureFlags,
                    telephonyFlags,
                    IncomingCallFilterGraph::new);

            mIncomingCallNotifier = incomingCallNotifier;
            incomingCallNotifier.setCallsManagerProxy(new IncomingCallNotifier.CallsManagerProxy() {
                @Override
                public boolean hasUnholdableCallsForOtherConnectionService(
                        PhoneAccountHandle phoneAccountHandle) {
                    return mCallsManager.hasUnholdableCallsForOtherConnectionService(
                            phoneAccountHandle);
                }

                @Override
                public int getNumUnholdableCallsForOtherConnectionService(
                        PhoneAccountHandle phoneAccountHandle) {
                    return mCallsManager.getNumUnholdableCallsForOtherConnectionService(
                            phoneAccountHandle);
                }

                @Override
                public Call getActiveCall() {
                    return mCallsManager.getActiveCall();
                }
            });
            mCallsManager.setIncomingCallNotifier(mIncomingCallNotifier);

            mRespondViaSmsManager = new RespondViaSmsManager(mCallsManager, mLock);
            mCallsManager.setRespondViaSmsManager(mRespondViaSmsManager);

            mContext.registerReceiverAsUser(mUserSwitchedReceiver, UserHandle.ALL,
                    USER_SWITCHED_FILTER, null, null);
            mContext.registerReceiverAsUser(mUserStartingReceiver, UserHandle.ALL,
                    USER_STARTING_FILTER, null, null);
            mContext.registerReceiverAsUser(mBootCompletedReceiver, UserHandle.ALL,
                    BOOT_COMPLETE_FILTER, null, null);

            // Set current user explicitly since USER_SWITCHED_FILTER intent can be missed at
            // startup
            synchronized (mLock) {
                UserHandle currentUserHandle = UserHandle.of(ActivityManager.getCurrentUser());
                mPhoneAccountRegistrar.setCurrentUserHandle(currentUserHandle);
                mCallsManager.onUserSwitch(currentUserHandle);
            }

            mCallIntentProcessor = new CallIntentProcessor(mContext, mCallsManager,
                    defaultDialerCache, featureFlags);
            mTelecomBroadcastIntentProcessor = new TelecomBroadcastIntentProcessor(
                    mContext, mCallsManager);

            // Register the receiver for the dialer secret codes, used to enable extended logging.
            mDialerCodeReceiver = new DialerCodeReceiver(mCallsManager);
            mContext.registerReceiver(mDialerCodeReceiver, DIALER_SECRET_CODE_FILTER,
                    Manifest.permission.CONTROL_INCALL_EXPERIENCE, null);

            // There is no USER_SWITCHED broadcast for user 0, handle it here explicitly.
            mTelecomServiceImpl = new TelecomServiceImpl(
                    mContext, mCallsManager, mPhoneAccountRegistrar,
                    new CallIntentProcessor.AdapterImpl(defaultDialerCache),
                    new UserCallIntentProcessorFactory() {
                        @Override
                        public UserCallIntentProcessor create(Context context,
                                UserHandle userHandle) {
                            return new UserCallIntentProcessor(context, userHandle, featureFlags);
                        }
                    },
                    defaultDialerCache,
                    new TelecomServiceImpl.SubscriptionManagerAdapterImpl(),
                    new TelecomServiceImpl.SettingsSecureAdapterImpl(),
                    featureFlags,
                    null,
                    mLock);
        } finally {
            Log.endSession();
        }
    }

    @VisibleForTesting
    public PhoneAccountRegistrar getPhoneAccountRegistrar() {
        return mPhoneAccountRegistrar;
    }

    @VisibleForTesting
    public CallsManager getCallsManager() {
        return mCallsManager;
    }

    public CallIntentProcessor getCallIntentProcessor() {
        return mCallIntentProcessor;
    }

    public TelecomBroadcastIntentProcessor getTelecomBroadcastIntentProcessor() {
        return mTelecomBroadcastIntentProcessor;
    }

    public TelecomServiceImpl getTelecomServiceImpl() {
        return mTelecomServiceImpl;
    }

    public Object getLock() {
        return mLock;
    }

    public boolean isBootComplete() {
        return mIsBootComplete;
    }

    public FeatureFlags getFeatureFlags() {
        return mFeatureFlags;
    }
}
