/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.pbap;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import android.annotation.RequiresPermission;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUtils;
import android.bluetooth.IBluetoothPbap;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.sysprop.BluetoothProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.IObexConnectionHandler;
import com.android.bluetooth.ObexServerSockets;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.InteropUtil;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.bluetooth.sdp.SdpManagerNativeInterface;
import com.android.bluetooth.util.DevicePolicyUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

// Next tag value for ContentProfileErrorReportUtils.report(): 12
public class BluetoothPbapService extends ProfileService implements IObexConnectionHandler {
    private static final String TAG = "BluetoothPbapService";

    /**
     * To enable PBAP DEBUG/VERBOSE logging - run below cmd in adb shell, and
     * restart com.android.bluetooth process. only enable DEBUG log:
     * "setprop log.tag.BluetoothPbapService DEBUG"; enable both VERBOSE and
     * DEBUG log: "setprop log.tag.BluetoothPbapService VERBOSE"
     */

    public static final boolean DEBUG = true;

    public static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * The component name of the owned BluetoothPbapActivity
     */
    private static final String PBAP_ACTIVITY = BluetoothPbapActivity.class.getCanonicalName();

    /**
     * Intent indicating incoming obex authentication request which is from
     * PCE(Carkit)
     */
    static final String AUTH_CHALL_ACTION = "com.android.bluetooth.pbap.authchall";

    /**
     * Intent indicating obex session key input complete by user which is sent
     * from BluetoothPbapActivity
     */
    static final String AUTH_RESPONSE_ACTION = "com.android.bluetooth.pbap.authresponse";

    /**
     * Intent indicating user canceled obex authentication session key input
     * which is sent from BluetoothPbapActivity
     */
    static final String AUTH_CANCELLED_ACTION = "com.android.bluetooth.pbap.authcancelled";

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothPbapActivity
     */
    static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.pbap.userconfirmtimeout";

    /**
     * Intent Extra name indicating session key which is sent from
     * BluetoothPbapActivity
     */
    static final String EXTRA_SESSION_KEY = "com.android.bluetooth.pbap.sessionkey";
    static final String EXTRA_DEVICE = "com.android.bluetooth.pbap.device";

    static final int MSG_ACQUIRE_WAKE_LOCK = 5004;
    static final int MSG_RELEASE_WAKE_LOCK = 5005;
    static final int MSG_STATE_MACHINE_DONE = 5006;

    static final int START_LISTENER = 1;
    static final int USER_TIMEOUT = 2;
    static final int SHUTDOWN = 3;
    static final int LOAD_CONTACTS = 4;
    static final int CONTACTS_LOADED = 5;
    static final int CHECK_SECONDARY_VERSION_COUNTER = 6;
    static final int ROLLOVER_COUNTERS = 7;
    static final int GET_LOCAL_TELEPHONY_DETAILS = 8;
    static final int HANDLE_VERSION_UPDATE_NOTIFICATION = 9;

    static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;
    static final int RELEASE_WAKE_LOCK_DELAY = 10000;

    private PowerManager.WakeLock mWakeLock;

    private static String sLocalPhoneNum;
    private static String sLocalPhoneName;

    private ObexServerSockets mServerSockets = null;
    private DatabaseManager mDatabaseManager;

    private static final int SDP_PBAP_SERVER_VERSION_1_2 = 0x0102;
    // PBAP v1.2.3, Sec. 7.1.2: local phonebook and favorites
    private static final int SDP_PBAP_SUPPORTED_REPOSITORIES_WITHOUT_SIM = 0x0009;
    private static final int SDP_PBAP_SUPPORTED_REPOSITORIES_WITH_SIM = 0x000B;
    private static final int SDP_PBAP_SUPPORTED_FEATURES = 0x021F;

    /* PBAP will use Bluetooth notification ID from 1000000 (included) to 2000000 (excluded).
       The notification ID should be unique in Bluetooth package. */
    private static final int PBAP_NOTIFICATION_ID_START = 1000000;
    private static final int PBAP_NOTIFICATION_ID_END = 2000000;
    static final int VERSION_UPDATE_NOTIFICATION_DELAY = 500; //in ms

    private int mSdpHandle = -1;

    private PbapHandler mSessionStatusHandler;
    private HandlerThread mHandlerThread;
    @VisibleForTesting
    final HashMap<BluetoothDevice, PbapStateMachine> mPbapStateMachineMap = new HashMap<>();
    private volatile int mNextNotificationId = PBAP_NOTIFICATION_ID_START;

    // package and class name to which we send intent to check phone book access permission
    private static final String ACCESS_AUTHORITY_PACKAGE = "com.android.settings";
    private static final String ACCESS_AUTHORITY_CLASS =
            "com.android.settings.bluetooth.BluetoothPermissionRequest";

    private Thread mThreadLoadContacts;
    private boolean mContactsLoaded = false;

    private Thread mThreadUpdateSecVersionCounter;

    private static BluetoothPbapService sBluetoothPbapService;

    private static final String PBAP_NOTIFICATION_ID = "pbap_notification";
    private static final String PBAP_NOTIFICATION_NAME = "BT_PBAP_ADVANCE_SUPPORT";
    private static final int PBAP_ADV_VERSION = 0x0102;
    private static NotificationManager sNotificationManager;

    private static boolean sIsPseDynamicVersionUpgradeEnabled;

    public BluetoothPbapService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfilePbapServerEnabled().orElse(false);
    }

    public static boolean isSimEnabled() {
        return BluetoothProperties.isProfilePbapSimEnabled().orElse(false);
    }

    private class BluetoothPbapContentObserver extends ContentObserver {
        BluetoothPbapContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, " onChange on contact uri ");
            sendUpdateRequest();
        }
    }

    private void sendUpdateRequest() {
        if (mContactsLoaded) {
            if (!mSessionStatusHandler.hasMessages(CHECK_SECONDARY_VERSION_COUNTER)) {
                mSessionStatusHandler.sendMessage(
                        mSessionStatusHandler.obtainMessage(CHECK_SECONDARY_VERSION_COUNTER));
            }
        }
    }

    private BluetoothPbapContentObserver mContactChangeObserver;

    private void parseIntent(final Intent intent) {
        String action = intent.getAction();
        if (DEBUG) {
            Log.d(TAG, "action: " + action);
        }
        if (BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY.equals(action)) {
            int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                    BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
            if (requestType != BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS) {
                return;
            }

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            synchronized (mPbapStateMachineMap) {
                PbapStateMachine sm = mPbapStateMachineMap.get(device);
                if (sm == null) {
                    Log.w(TAG, "device not connected! device=" + device);
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.PBAP,
                            BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                            0);
                    return;
                }
                mSessionStatusHandler.removeMessages(USER_TIMEOUT, sm);
                int access = intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                        BluetoothDevice.CONNECTION_ACCESS_NO);
                boolean savePreference = intent.getBooleanExtra(
                        BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false);

                if (access == BluetoothDevice.CONNECTION_ACCESS_YES) {
                    if (savePreference) {
                        device.setPhonebookAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
                        if (VERBOSE) {
                            Log.v(TAG, "setPhonebookAccessPermission(ACCESS_ALLOWED)");
                        }
                    }
                    sm.sendMessage(PbapStateMachine.AUTHORIZED);
                } else {
                    if (savePreference) {
                        device.setPhonebookAccessPermission(BluetoothDevice.ACCESS_REJECTED);
                        if (VERBOSE) {
                            Log.v(TAG, "setPhonebookAccessPermission(ACCESS_REJECTED)");
                        }
                    }
                    sm.sendMessage(PbapStateMachine.REJECTED);
                }
            }
        } else if (AUTH_RESPONSE_ACTION.equals(action)) {
            String sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY);
            BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
            synchronized (mPbapStateMachineMap) {
                PbapStateMachine sm = mPbapStateMachineMap.get(device);
                if (sm == null) {
                    return;
                }
                Message msg = sm.obtainMessage(PbapStateMachine.AUTH_KEY_INPUT, sessionKey);
                sm.sendMessage(msg);
            }
        } else if (AUTH_CANCELLED_ACTION.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
            synchronized (mPbapStateMachineMap) {
                PbapStateMachine sm = mPbapStateMachineMap.get(device);
                if (sm == null) {
                    return;
                }
                sm.sendMessage(PbapStateMachine.AUTH_CANCELLED);
            }
        } else {
            Log.w(TAG, "Unhandled intent action: " + action);
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                    1);
        }
    }

    /** Process a change in the bonding state for a device */
    public void handleBondStateChanged(BluetoothDevice device, int fromState, int toState) {
        if (toState == BluetoothDevice.BOND_BONDED && sIsPseDynamicVersionUpgradeEnabled) {
            mSessionStatusHandler.sendMessageDelayed(
                    mSessionStatusHandler.obtainMessage(HANDLE_VERSION_UPDATE_NOTIFICATION, device),
                    VERSION_UPDATE_NOTIFICATION_DELAY);
        }
    }

    private final BroadcastReceiver mUserChangeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    // EXTRA_USER_HANDLE is sent for both ACTION_USER_SWITCHED and
                    // ACTION_USER_UNLOCKED (even if the documentation doesn't mention it)
                    final int userId =
                            intent.getIntExtra(
                                    Intent.EXTRA_USER_HANDLE,
                                    BluetoothUtils.USER_HANDLE_NULL.getIdentifier());
                    if (userId == BluetoothUtils.USER_HANDLE_NULL.getIdentifier()) {
                        Log.e(TAG, "userChangeReceiver received an invalid EXTRA_USER_HANDLE");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                                2);
                        return;
                    }
                    Log.d(TAG, "Got " + action + " to userId " + userId);
                    UserManager userManager = getSystemService(UserManager.class);
                    if (userManager.isUserUnlocked(UserHandle.of(userId))) {
                        sendUpdateRequest();
                    }
                }
            };

    @VisibleForTesting
    BroadcastReceiver mPbapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            parseIntent(intent);
        }
    };

    private void closeService() {
        if (VERBOSE) {
            Log.v(TAG, "Pbap Service closeService");
        }

        BluetoothPbapUtils.savePbapParams(this);

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        cleanUpServerSocket();

        if (mSessionStatusHandler != null) {
            mSessionStatusHandler.removeCallbacksAndMessages(null);
        }
    }

    private void cleanUpServerSocket() {
        // Step 1, 2: clean up active server session and connection socket
        synchronized (mPbapStateMachineMap) {
            for (PbapStateMachine stateMachine : mPbapStateMachineMap.values()) {
                stateMachine.sendMessage(PbapStateMachine.DISCONNECT);
            }
        }
        // Step 3: clean up SDP record
        cleanUpSdpRecord();
        // Step 4: clean up existing server sockets
        if (mServerSockets != null) {
            mServerSockets.shutdown(false);
            mServerSockets = null;
        }
    }

    private void createSdpRecord() {
        if (mSdpHandle > -1) {
            Log.w(TAG, "createSdpRecord, SDP record already created");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                    3);
            return;
        }

        int pbapSupportedRepositories = isSimEnabled() ? SDP_PBAP_SUPPORTED_REPOSITORIES_WITH_SIM
                : SDP_PBAP_SUPPORTED_REPOSITORIES_WITHOUT_SIM;

        mSdpHandle =
                SdpManagerNativeInterface.getInstance()
                        .createPbapPseRecord(
                                "OBEX Phonebook Access Server",
                                mServerSockets.getRfcommChannel(),
                                mServerSockets.getL2capPsm(),
                                SDP_PBAP_SERVER_VERSION_1_2,
                                pbapSupportedRepositories,
                                SDP_PBAP_SUPPORTED_FEATURES);

        if (DEBUG) {
            Log.d(TAG, "created Sdp record, mSdpHandle=" + mSdpHandle);
        }
    }

    private void cleanUpSdpRecord() {
        if (mSdpHandle < 0) {
            Log.w(TAG, "cleanUpSdpRecord, SDP record never created");
            return;
        }
        int sdpHandle = mSdpHandle;
        mSdpHandle = -1;
        SdpManagerNativeInterface nativeInterface = SdpManagerNativeInterface.getInstance();
        if (DEBUG) {
            Log.d(TAG, "cleanUpSdpRecord, mSdpHandle=" + sdpHandle);
        }
        if (!nativeInterface.isAvailable()) {
            Log.e(TAG, "SdpManagerNativeInterface is not available");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    4);
        } else if (!nativeInterface.removeSdpRecord(sdpHandle)) {
            Log.w(TAG, "cleanUpSdpRecord, removeSdpRecord failed, sdpHandle=" + sdpHandle);
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                    5);
        }
    }

    /*Creates Notification for PBAP version upgrade */
    protected static void createNotification(BluetoothPbapService context) {
        if (VERBOSE) Log.v(TAG, "Create PBAP Notification for Upgrade");
        // create Notification channel.
        sNotificationManager = context.getSystemService(NotificationManager.class);
        if (sNotificationManager != null) {
            NotificationChannel mChannel = new NotificationChannel(PBAP_NOTIFICATION_ID,
                    PBAP_NOTIFICATION_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            sNotificationManager.createNotificationChannel(mChannel);
            // create notification
            String title = context.getString(R.string.phonebook_advance_feature_support);
            String contentText = context.getString(R.string.repair_for_adv_phonebook_feature);
            int notificationId = android.R.drawable.stat_sys_data_bluetooth;
            Notification notification = new Notification.Builder(context, PBAP_NOTIFICATION_ID)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setSmallIcon(notificationId)
                    .setAutoCancel(true)
                    .build();
            sNotificationManager.notify(notificationId, notification);
        } else {
            Log.e(TAG, "sNotificationManager is null");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    6);
        }

    }

    /* Checks if notification for Version Upgrade is required */
    protected static void handleNotificationTask(BluetoothPbapService service,
            BluetoothDevice remoteDevice) {
        int pce_version = 0;

        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService != null) {
            pce_version = adapterService.getRemotePbapPceVersion(remoteDevice.getAddress());
            Log.d(TAG, "pce_version: " + pce_version);
        }

        boolean matched = InteropUtil.interopMatchAddrOrName(
                InteropUtil.InteropFeature.INTEROP_ADV_PBAP_VER_1_2, remoteDevice.getAddress());
        Log.d(TAG, "INTEROP_ADV_PBAP_VER_1_2: matched=" + matched);

        if (pce_version == PBAP_ADV_VERSION && !matched) {
            Log.d(TAG, "Remote Supports PBAP 1.2. Notify user");
            createNotification(service);
        } else {
            Log.d(TAG, "Notification Not Required.");
            if (sNotificationManager != null) {
                sNotificationManager.cancelAll();
            }
        }
    }
    private class PbapHandler extends Handler {
        private PbapHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) {
                Log.v(TAG, "Handler(): got msg=" + msg.what);
            }

            switch (msg.what) {
                case START_LISTENER:
                    mServerSockets = ObexServerSockets.create(BluetoothPbapService.this);
                    if (mServerSockets == null) {
                        Log.w(TAG, "ObexServerSockets.create() returned null");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                                7);
                        break;
                    }
                    createSdpRecord();
                    // fetch Pbap Params to check if significant change has happened to Database
                    BluetoothPbapUtils.fetchPbapParams(BluetoothPbapService.this);
                    break;
                case USER_TIMEOUT:
                    Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                    intent.setPackage(SystemProperties.get(
                            Utils.PAIRING_UI_PROPERTY,
                            getString(R.string.pairing_ui_package)));
                    PbapStateMachine stateMachine = (PbapStateMachine) msg.obj;
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, stateMachine.getRemoteDevice());
                    intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                            BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                    Utils.sendBroadcast(BluetoothPbapService.this, intent, BLUETOOTH_CONNECT,
                            Utils.getTempAllowlistBroadcastOptions());
                    stateMachine.sendMessage(PbapStateMachine.REJECTED);
                    break;
                case MSG_ACQUIRE_WAKE_LOCK:
                    if (mWakeLock == null) {
                        PowerManager pm = getSystemService(PowerManager.class);
                        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "StartingObexPbapTransaction");
                        mWakeLock.setReferenceCounted(false);
                        mWakeLock.acquire();
                        Log.w(TAG, "Acquire Wake Lock");
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.PBAP,
                                BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                                8);
                    }
                    mSessionStatusHandler.removeMessages(MSG_RELEASE_WAKE_LOCK);
                    mSessionStatusHandler.sendMessageDelayed(
                            mSessionStatusHandler.obtainMessage(MSG_RELEASE_WAKE_LOCK),
                            RELEASE_WAKE_LOCK_DELAY);
                    break;
                case MSG_RELEASE_WAKE_LOCK:
                    if (mWakeLock != null) {
                        mWakeLock.release();
                        mWakeLock = null;
                    }
                    break;
                case SHUTDOWN:
                    closeService();
                    break;
                case LOAD_CONTACTS:
                    loadAllContacts();
                    break;
                case CONTACTS_LOADED:
                    mContactsLoaded = true;
                    break;
                case CHECK_SECONDARY_VERSION_COUNTER:
                    updateSecondaryVersion();
                    break;
                case ROLLOVER_COUNTERS:
                    BluetoothPbapUtils.rolloverCounters();
                    break;
                case MSG_STATE_MACHINE_DONE:
                    PbapStateMachine sm = (PbapStateMachine) msg.obj;
                    BluetoothDevice remoteDevice = sm.getRemoteDevice();
                    sm.quitNow();
                    synchronized (mPbapStateMachineMap) {
                        mPbapStateMachineMap.remove(remoteDevice);
                    }
                    break;
                case GET_LOCAL_TELEPHONY_DETAILS:
                    getLocalTelephonyDetails();
                    break;
                case HANDLE_VERSION_UPDATE_NOTIFICATION:
                    BluetoothDevice remoteDev = (BluetoothDevice) msg.obj;

                    handleNotificationTask(sBluetoothPbapService, remoteDev);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Get the current connection state of PBAP with the passed in device
     *
     * @param device is the device whose connection state to PBAP we are trying to get
     * @return current connection state, one of {@link BluetoothProfile#STATE_DISCONNECTED},
     * {@link BluetoothProfile#STATE_CONNECTING}, {@link BluetoothProfile#STATE_CONNECTED}, or
     * {@link BluetoothProfile#STATE_DISCONNECTING}
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        synchronized (mPbapStateMachineMap) {
            PbapStateMachine sm = mPbapStateMachineMap.get(device);
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
        }
    }

    List<BluetoothDevice> getConnectedDevices() {
        synchronized (mPbapStateMachineMap) {
            return new ArrayList<>(mPbapStateMachineMap.keySet());
        }
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (states == null) {
            return devices;
        }
        synchronized (mPbapStateMachineMap) {
            for (int state : states) {
                for (BluetoothDevice device : mPbapStateMachineMap.keySet()) {
                    if (state == mPbapStateMachineMap.get(device).getConnectionState()) {
                        devices.add(device);
                    }
                }
            }
        }
        return devices;
    }

    /**
     * Set connection policy of the profile and tries to disconnect it if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN}
     *
     * <p> The device should already be paired.
     * Connection policy can be one of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Paired bluetooth device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true if connectionPolicy is set, false on error
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        enforceCallingOrSelfPermission(
                BLUETOOTH_PRIVILEGED, "Need BLUETOOTH_PRIVILEGED permission");
        if (DEBUG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.PBAP,
                  connectionPolicy)) {
            return false;
        }
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    /**
     * Get the connection policy of the profile.
     *
     * <p> The connection policy can be any of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Bluetooth device
     * @return connection policy of the device
     * @hide
     */
    public int getConnectionPolicy(BluetoothDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("Null device");
        }
        return mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.PBAP);
    }

    /**
     * Disconnects pbap server profile with device
     * @param device is the remote bluetooth device
     */
    public void disconnect(BluetoothDevice device) {
        synchronized (mPbapStateMachineMap) {
            PbapStateMachine sm = mPbapStateMachineMap.get(device);
            if (sm != null) {
                sm.sendMessage(PbapStateMachine.DISCONNECT);
            }
        }
    }

    static String getLocalPhoneNum() {
        return sLocalPhoneNum;
    }

    @VisibleForTesting
    static void setLocalPhoneName(String localPhoneName) {
        sLocalPhoneName = localPhoneName;
    }

    static String getLocalPhoneName() {
        return sLocalPhoneName;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new PbapBinder(this);
    }

    @Override
    public void start() {
        if (VERBOSE) {
            Log.v(TAG, "start()");
        }
        mDatabaseManager = Objects.requireNonNull(AdapterService.getAdapterService().getDatabase(),
            "DatabaseManager cannot be null when PbapService starts");

        IntentFilter userFilter = new IntentFilter();
        userFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        userFilter.addAction(Intent.ACTION_USER_SWITCHED);
        userFilter.addAction(Intent.ACTION_USER_UNLOCKED);

        getApplicationContext().registerReceiver(mUserChangeReceiver, userFilter);

        // Enable owned Activity component
        setComponentAvailable(PBAP_ACTIVITY, true);

        mContactsLoaded = false;
        mHandlerThread = new HandlerThread("PbapHandlerThread");
        BluetoothMethodProxy mp = BluetoothMethodProxy.getInstance();
        mp.threadStart(mHandlerThread);
        mSessionStatusHandler = new PbapHandler(mp.handlerThreadGetLooper(mHandlerThread));
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(AUTH_RESPONSE_ACTION);
        filter.addAction(AUTH_CANCELLED_ACTION);
        BluetoothPbapConfig.init(this);
        registerReceiver(mPbapReceiver, filter);
        try {
            mContactChangeObserver = new BluetoothPbapContentObserver();
            getContentResolver().registerContentObserver(
                    DevicePolicyUtils.getEnterprisePhoneUri(this), false,
                    mContactChangeObserver);
        } catch (SQLiteException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    9);
            Log.e(TAG, "SQLite exception: " + e);
        } catch (IllegalStateException e) {
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.PBAP,
                    BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                    10);
            Log.e(TAG, "Illegal state exception, content observer is already registered");
        }

        setBluetoothPbapService(this);

        mSessionStatusHandler.sendMessage(
                mSessionStatusHandler.obtainMessage(GET_LOCAL_TELEPHONY_DETAILS));
        mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(LOAD_CONTACTS));
        mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(START_LISTENER));

        AdapterService adapterService = AdapterService.getAdapterService();
        if (adapterService != null) {
            sIsPseDynamicVersionUpgradeEnabled =
                    adapterService.pbapPseDynamicVersionUpgradeIsEnabled();
            Log.d(TAG, "sIsPseDynamicVersionUpgradeEnabled: " + sIsPseDynamicVersionUpgradeEnabled);
        }
    }

    @Override
    public void stop() {
        if (VERBOSE) {
            Log.v(TAG, "stop()");
        }
        setBluetoothPbapService(null);
        if (mSessionStatusHandler != null) {
            mSessionStatusHandler.obtainMessage(SHUTDOWN).sendToTarget();
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        mContactsLoaded = false;
        if (mContactChangeObserver == null) {
            Log.i(TAG, "Avoid unregister when receiver it is not registered");
            return;
        }
        unregisterReceiver(mPbapReceiver);
        getContentResolver().unregisterContentObserver(mContactChangeObserver);
        mContactChangeObserver = null;
        setComponentAvailable(PBAP_ACTIVITY, false);
        synchronized (mPbapStateMachineMap) {
            mPbapStateMachineMap.clear();
        }
        getApplicationContext().unregisterReceiver(mUserChangeReceiver);
    }

    /**
     * Get the current instance of {@link BluetoothPbapService}
     *
     * @return current instance of {@link BluetoothPbapService}
     */
    @VisibleForTesting
    public static synchronized BluetoothPbapService getBluetoothPbapService() {
        if (sBluetoothPbapService == null) {
            Log.w(TAG, "getBluetoothPbapService(): service is null");
            return null;
        }
        if (!sBluetoothPbapService.isAvailable()) {
            Log.w(TAG, "getBluetoothPbapService(): service is not available");
            return null;
        }
        return sBluetoothPbapService;
    }

    private static synchronized void setBluetoothPbapService(BluetoothPbapService instance) {
        if (DEBUG) {
            Log.d(TAG, "setBluetoothPbapService(): set to: " + instance);
        }
        sBluetoothPbapService = instance;
    }

    @VisibleForTesting
    static class PbapBinder extends IBluetoothPbap.Stub implements IProfileServiceBinder {
        private BluetoothPbapService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private BluetoothPbapService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        PbapBinder(BluetoothPbapService service) {
            if (VERBOSE) {
                Log.v(TAG, "PbapBinder()");
            }
            mService = service;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public List<BluetoothDevice> getConnectedDevices(AttributionSource source) {
            if (DEBUG) {
                Log.d(TAG, "getConnectedDevices");
            }
            BluetoothPbapService service = getService(source);
            if (service == null) {
                return new ArrayList<>(0);
            }
            return service.getConnectedDevices();
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source) {
            if (DEBUG) {
                Log.d(TAG, "getDevicesMatchingConnectionStates");
            }
            BluetoothPbapService service = getService(source);
            if (service == null) {
                return new ArrayList<>(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        @Override
        public int getConnectionState(BluetoothDevice device, AttributionSource source) {
            if (DEBUG) {
                Log.d(TAG, "getConnectionState: " + device);
            }
            BluetoothPbapService service = getService(source);
            if (service == null) {
                return BluetoothAdapter.STATE_DISCONNECTED;
            }
            return service.getConnectionState(device);
        }

        @Override
        public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source) {
            if (DEBUG) {
                Log.d(TAG, "setConnectionPolicy for device: " + device + ", policy:"
                        + connectionPolicy);
            }
            BluetoothPbapService service = getService(source);
            if (service == null) {
                return false;
            }
            return service.setConnectionPolicy(device, connectionPolicy);
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source) {
            if (DEBUG) {
                Log.d(TAG, "disconnect");
            }
            BluetoothPbapService service = getService(source);
            if (service == null) {
                return;
            }
            service.disconnect(device);
        }
    }

    @Override
    public boolean onConnect(BluetoothDevice remoteDevice, BluetoothSocket socket) {
        if (remoteDevice == null || socket == null) {
            Log.e(TAG, "onConnect(): Unexpected null. remoteDevice=" + remoteDevice
                    + " socket=" + socket);
            return false;
        }

        PbapStateMachine sm = PbapStateMachine.make(this, mHandlerThread.getLooper(), remoteDevice,
                socket,  this, mSessionStatusHandler, mNextNotificationId);
        mNextNotificationId++;
        if (mNextNotificationId == PBAP_NOTIFICATION_ID_END) {
            mNextNotificationId = PBAP_NOTIFICATION_ID_START;
        }
        synchronized (mPbapStateMachineMap) {
            mPbapStateMachineMap.put(remoteDevice, sm);
        }
        sm.sendMessage(PbapStateMachine.REQUEST_PERMISSION);
        return true;
    }

    /**
     * Get the phonebook access permission for the device; if unknown, ask the user.
     * Send the result to the state machine.
     * @param stateMachine PbapStateMachine which sends the request
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public void checkOrGetPhonebookPermission(PbapStateMachine stateMachine) {
        BluetoothDevice device = stateMachine.getRemoteDevice();
        int permission = device.getPhonebookAccessPermission();
        if (DEBUG) {
            Log.d(TAG, "getPhonebookAccessPermission() = " + permission);
        }

        if (permission == BluetoothDevice.ACCESS_ALLOWED) {
            setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            stateMachine.sendMessage(PbapStateMachine.AUTHORIZED);
        } else if (permission == BluetoothDevice.ACCESS_REJECTED) {
            stateMachine.sendMessage(PbapStateMachine.REJECTED);
        } else { // permission == BluetoothDevice.ACCESS_UNKNOWN
            Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
            intent.setClassName(BluetoothPbapService.ACCESS_AUTHORITY_PACKAGE,
                    BluetoothPbapService.ACCESS_AUTHORITY_CLASS);
            intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                    BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothDevice.EXTRA_PACKAGE_NAME, this.getPackageName());
            Utils.sendOrderedBroadcast(this, intent, BLUETOOTH_CONNECT,
                    Utils.getTempAllowlistBroadcastOptions(), null/* resultReceiver */,
                    null/* scheduler */, Activity.RESULT_OK/* initialCode */, null/* initialData */,
                    null/* initialExtras */);
            if (VERBOSE) {
                Log.v(TAG, "waiting for authorization for connection from: " + device);
            }
            /* In case car kit time out and try to use HFP for phonebook
             * access, while UI still there waiting for user to confirm */
            Message msg = mSessionStatusHandler.obtainMessage(BluetoothPbapService.USER_TIMEOUT,
                    stateMachine);
            mSessionStatusHandler.sendMessageDelayed(msg, USER_CONFIRM_TIMEOUT_VALUE);
            /* We will continue the process when we receive
             * BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY from Settings app. */
        }
    }

    /**
     * Called when an unrecoverable error occurred in an accept thread.
     * Close down the server socket, and restart.
     */
    @Override
    public synchronized void onAcceptFailed() {
        Log.w(TAG, "PBAP server socket accept thread failed. Restarting the server socket");
        ContentProfileErrorReportUtils.report(
                BluetoothProfile.PBAP,
                BluetoothProtoEnums.BLUETOOTH_PBAP_SERVICE,
                BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                11);

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        cleanUpServerSocket();

        if (mSessionStatusHandler != null) {
            mSessionStatusHandler.removeCallbacksAndMessages(null);
        }

        synchronized (mPbapStateMachineMap) {
            mPbapStateMachineMap.clear();
        }

        mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(START_LISTENER));
    }

    private void loadAllContacts() {
        if (mThreadLoadContacts == null) {
            Runnable r =
                    new Runnable() {
                        @Override
                        public void run() {
                            BluetoothPbapUtils.loadAllContacts(
                                    BluetoothPbapService.this, mSessionStatusHandler);
                            mThreadLoadContacts = null;
                        }
                    };
            mThreadLoadContacts = new Thread(r);
            mThreadLoadContacts.start();
        }
    }

    private void updateSecondaryVersion() {
        if (mThreadUpdateSecVersionCounter == null) {
            Runnable r =
                    new Runnable() {
                        @Override
                        public void run() {
                            BluetoothPbapUtils.updateSecondaryVersionCounter(
                                    BluetoothPbapService.this, mSessionStatusHandler);
                            mThreadUpdateSecVersionCounter = null;
                        }
                    };
            mThreadUpdateSecVersionCounter = new Thread(r);
            mThreadUpdateSecVersionCounter.start();
        }
    }

    private void getLocalTelephonyDetails() {
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        if (tm != null) {
            sLocalPhoneNum = tm.getLine1Number();
            sLocalPhoneName = this.getString(R.string.localPhoneName);
        }
        if (VERBOSE)
            Log.v(TAG, "Local Phone Details- Number:" + sLocalPhoneNum
                    + ", Name:" + sLocalPhoneName);
    }
}
