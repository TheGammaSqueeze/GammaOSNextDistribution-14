/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AppOpsManager;
import android.app.GameManager;
import android.app.GameModeConfiguration;
import android.app.GameState;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.usage.NetworkStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaDrm;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.cts.util.CtsNetUtils;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.StatsEvent;
import android.util.StatsLog;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;

import libcore.javax.net.ssl.TestSSLContext;
import libcore.javax.net.ssl.TestSSLSocketPair;

import org.junit.Assert;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLSocket;

public class AtomTests {
    private static final String TAG = AtomTests.class.getSimpleName();

    private static final String MY_PACKAGE_NAME = "com.android.server.cts.device.statsdatom";

    private static final Map<String, Integer> APP_OPS_ENUM_MAP = new ArrayMap<>();

    static {
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_COARSE_LOCATION, 0);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_FINE_LOCATION, 1);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_GPS, 2);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_VIBRATE, 3);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_CONTACTS, 4);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_CONTACTS, 5);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_CALL_LOG, 6);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_CALL_LOG, 7);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_CALENDAR, 8);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_CALENDAR, 9);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WIFI_SCAN, 10);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_POST_NOTIFICATION, 11);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_NEIGHBORING_CELLS, 12);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CALL_PHONE, 13);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_SMS, 14);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_SMS, 15);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_SMS, 16);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_EMERGENCY_BROADCAST, 17);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_MMS, 18);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_WAP_PUSH, 19);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SEND_SMS, 20);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_ICC_SMS, 21);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_ICC_SMS, 22);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_SETTINGS, 23);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, 24);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS, 25);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CAMERA, 26);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECORD_AUDIO, 27);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PLAY_AUDIO, 28);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_CLIPBOARD, 29);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_CLIPBOARD, 30);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_TAKE_MEDIA_BUTTONS, 31);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_TAKE_AUDIO_FOCUS, 32);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_MASTER_VOLUME, 33);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_VOICE_VOLUME, 34);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_RING_VOLUME, 35);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_MEDIA_VOLUME, 36);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_ALARM_VOLUME, 37);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_NOTIFICATION_VOLUME, 38);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_BLUETOOTH_VOLUME, 39);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WAKE_LOCK, 40);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MONITOR_LOCATION, 41);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MONITOR_HIGH_POWER_LOCATION, 42);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_GET_USAGE_STATS, 43);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MUTE_MICROPHONE, 44);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_TOAST_WINDOW, 45);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PROJECT_MEDIA, 46);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACTIVATE_VPN, 47);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_WALLPAPER, 48);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ASSIST_STRUCTURE, 49);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ASSIST_SCREENSHOT, 50);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_PHONE_STATE, 51);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ADD_VOICEMAIL, 52);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_USE_SIP, 53);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS, 54);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_USE_FINGERPRINT, 55);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_BODY_SENSORS, 56);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_CELL_BROADCASTS, 57);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MOCK_LOCATION, 58);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE, 59);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE, 60);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_TURN_SCREEN_ON, 61);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_GET_ACCOUNTS, 62);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RUN_IN_BACKGROUND, 63);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUDIO_ACCESSIBILITY_VOLUME, 64);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_PHONE_NUMBERS, 65);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES, 66);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, 67);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_INSTANT_APP_START_FOREGROUND, 68);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ANSWER_PHONE_CALLS, 69);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RUN_ANY_IN_BACKGROUND, 70);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, 71);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_REQUEST_DELETE_PACKAGES, 72);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE, 73);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACCEPT_HANDOVER, 74);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MANAGE_IPSEC_TUNNELS, 75);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_START_FOREGROUND, 76);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_BLUETOOTH_SCAN, 77);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_USE_BIOMETRIC, 78);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACTIVITY_RECOGNITION, 79);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SMS_FINANCIAL_TRANSACTIONS, 80);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_MEDIA_AUDIO, 81);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_MEDIA_AUDIO, 82);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_MEDIA_VIDEO, 83);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO, 84);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_MEDIA_IMAGES, 85);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, 86);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_LEGACY_STORAGE, 87);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACCESS_ACCESSIBILITY, 88);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_DEVICE_IDENTIFIERS, 89);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACCESS_MEDIA_LOCATION, 90);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_QUERY_ALL_PACKAGES, 91);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE, 92);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, 93);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACTIVATE_PLATFORM_VPN, 94);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_LOADER_USAGE_STATS, 95);
        // Op 96 was deprecated/removed
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, 97);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER, 98);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_NO_ISOLATED_STORAGE, 99);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_PHONE_CALL_CAMERA, 101);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD, 102);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS, 103);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MANAGE_CREDENTIALS, 104);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, 105);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECORD_AUDIO_OUTPUT, 106);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM, 107);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_FINE_LOCATION_SOURCE, 108);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_COARSE_LOCATION_SOURCE, 109);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MANAGE_MEDIA, 110);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_BLUETOOTH_CONNECT, 111);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_UWB_RANGING, 112);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACTIVITY_RECOGNITION_SOURCE, 113);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_BLUETOOTH_ADVERTISE, 114);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECORD_INCOMING_PHONE_AUDIO, 115);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_NEARBY_WIFI_DEVICES, 116);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ESTABLISH_VPN_SERVICE, 117);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ESTABLISH_VPN_MANAGER, 118);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS, 119);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO, 120);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO, 121);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RUN_USER_INITIATED_JOBS, 122);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_MEDIA_VISUAL_USER_SELECTED, 123);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION, 124);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS, 125);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_WRITE_HEALTH_DATA, 126);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_FOREGROUND_SERVICE_SPECIAL_USE, 127);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS, 128);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION, 129);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION,
                130);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CAPTURE_CONSENTLESS_BUGREPORT_ON_USERDEBUG_BUILD,
                131);
        // Op 132 was deprecated
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_USE_FULL_SCREEN_INTENT, 133);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CAMERA_SANDBOXED, 134);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECORD_AUDIO_SANDBOXED, 135);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_SANDBOX_TRIGGER_AUDIO, 136);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA, 137);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_CREATE_ACCESSIBILITY_OVERLAY, 138);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_MEDIA_ROUTING_CONTROL, 139);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ENABLE_MOBILE_DATA_BY_USER, 140);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RESERVED_FOR_TESTING, 141);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RAPID_CLEAR_NOTIFICATIONS_BY_LISTENER, 142);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_READ_SYSTEM_GRAMMATICAL_GENDER, 143);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_RUN_BACKUP_JOBS, 144);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_ARCHIVE_ICON_OVERLAY, 145);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_UNARCHIVAL_CONFIRMATION, 146);
        APP_OPS_ENUM_MAP.put(AppOpsManager.OPSTR_EMERGENCY_LOCATION, 147);
    }

    @Test
    public void testTlsHandshake() throws Exception {
        TestSSLContext context = TestSSLContext.create();
        SSLSocket[] sockets = TestSSLSocketPair.connect(context, null, null);

        if (sockets.length < 2) {
            return;
        }
        sockets[0].getOutputStream().write(42);
        Assert.assertEquals(42, sockets[1].getInputStream().read());
        sockets[0].close();
        sockets[1].close();
    }

    @Test
    // Start the isolated service, which logs an AppBreadcrumbReported atom, and then exit.
    public void testIsolatedProcessService() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent(context, IsolatedProcessService.class);
        context.startService(intent);
        sleep(2_000);
        context.stopService(intent);
    }

    @Test
    public void testAudioState() {
        // TODO: This should surely be getTargetContext(), here and everywhere, but test first.
        Context context = InstrumentationRegistry.getContext();
        MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.good);
        mediaPlayer.start();
        sleep(2_000);
        mediaPlayer.stop();
    }

    @Test
    public void testBleScanOpportunistic() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC).build();
        performBleScan(scanSettings, null, false);
    }

    @Test
    public void testBleScanUnoptimized() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        performBleScan(scanSettings, null, false);
    }

    @Test
    public void testBleScanResult() {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        ScanFilter.Builder scanFilter = new ScanFilter.Builder();
        performBleScan(scanSettings, Arrays.asList(scanFilter.build()), true);
    }

    @Test
    public void testBleScanInterrupted() throws Exception {
        performBleAction((bluetoothAdapter, bleScanner) -> {
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            ScanCallback scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.v(TAG, "called onScanResult");
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.v(TAG, "called onScanFailed");
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.v(TAG, "called onBatchScanResults");
                }
            };

            int uid = Process.myUid();
            int whatAtomId = 9_999;

            // Get the current setting for bluetooth background scanning.
            // Set to 0 if the setting is not found or an error occurs.
            int initialBleScanGlobalSetting = Settings.Global.getInt(
                    InstrumentationRegistry.getTargetContext().getContentResolver(),
                    Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0);

            // Turn off bluetooth background scanning.
            Settings.Global.putInt(InstrumentationRegistry.getTargetContext().getContentResolver(),
                    Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0);

            // Change state to State.ON.
            bleScanner.startScan(null, scanSettings, scanCallback);
            sleep(6_000);
            writeSliceByBleScanStateChangedAtom(whatAtomId, uid, false, false, false);
            writeSliceByBleScanStateChangedAtom(whatAtomId, uid, false, false, false);

            bluetoothAdapter.disable();
            sleep(6_000);

            // Trigger State.RESET so that new state is State.OFF.
            if (!bluetoothAdapter.enable()) {
                Log.e(TAG, "Could not enable bluetooth to trigger state reset");
                return;
            }
            sleep(6_000); // Wait for Bluetooth to fully turn on.
            writeSliceByBleScanStateChangedAtom(whatAtomId, uid, false, false, false);
            writeSliceByBleScanStateChangedAtom(whatAtomId, uid, false, false, false);
            writeSliceByBleScanStateChangedAtom(whatAtomId, uid, false, false, false);

            // Set bluetooth background scanning to original setting.
            Settings.Global.putInt(InstrumentationRegistry.getTargetContext().getContentResolver(),
                    Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, initialBleScanGlobalSetting);
        });
    }

    private static void writeSliceByBleScanStateChangedAtom(int atomId, int firstUid,
            boolean field2, boolean field3,
            boolean field4) {
        final StatsEvent.Builder builder = StatsEvent.newBuilder()
                .setAtomId(atomId)
                .writeAttributionChain(new int[]{firstUid}, new String[]{"tag1"})
                .writeBoolean(field2)
                .writeBoolean(field3)
                .writeBoolean(field4)
                .usePooledBuffer();

        StatsLog.write(builder.build());
    }

    /**
     * Set up BluetoothLeScanner and perform the action in the callback.
     * Restore Bluetooth to original state afterwards.
     **/
    private static void performBleAction(BiConsumer<BluetoothAdapter, BluetoothLeScanner> actions) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device does not support Bluetooth");
            return;
        }
        boolean bluetoothEnabledByTest = false;
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.enable()) {
                Log.e(TAG, "Bluetooth is not enabled");
                return;
            }
            sleep(2_000); // Wait for Bluetooth to fully turn on.
            bluetoothEnabledByTest = true;
        }
        BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.e(TAG, "Cannot access BLE scanner");
            return;
        }

        actions.accept(bluetoothAdapter, bleScanner);

        // Restore adapter state
        if (bluetoothEnabledByTest) {
            bluetoothAdapter.disable();
        }
    }


    private static void performBleScan(ScanSettings scanSettings, List<ScanFilter> scanFilters,
            boolean waitForResult) {
        performBleAction((bluetoothAdapter, bleScanner) -> {
            CountDownLatch resultsLatch = new CountDownLatch(1);
            ScanCallback scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.v(TAG, "called onScanResult");
                    resultsLatch.countDown();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.v(TAG, "called onScanFailed");
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.v(TAG, "called onBatchScanResults");
                    resultsLatch.countDown();
                }
            };

            bleScanner.startScan(scanFilters, scanSettings, scanCallback);
            if (waitForResult) {
                waitForReceiver(InstrumentationRegistry.getContext(), 59_000, resultsLatch, null);
            } else {
                sleep(2_000);
            }
            bleScanner.stopScan(scanCallback);
        });
    }

    @Test
    public void testCameraState() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        CameraManager cam = context.getSystemService(CameraManager.class);
        String[] cameraIds = cam.getCameraIdList();
        if (cameraIds.length == 0) {
            Log.e(TAG, "No camera found on device");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        final CameraDevice.StateCallback cb = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cd) {
                Log.i(TAG, "CameraDevice " + cd.getId() + " opened");
                sleep(2_000);
                cd.close();
            }

            @Override
            public void onClosed(CameraDevice cd) {
                latch.countDown();
                Log.i(TAG, "CameraDevice " + cd.getId() + " closed");
            }

            @Override
            public void onDisconnected(CameraDevice cd) {
                Log.w(TAG, "CameraDevice  " + cd.getId() + " disconnected");
            }

            @Override
            public void onError(CameraDevice cd, int error) {
                Log.e(TAG, "CameraDevice " + cd.getId() + "had error " + error);
            }
        };

        HandlerThread handlerThread = new HandlerThread("br_handler_thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);

        cam.openCamera(cameraIds[0], cb, handler);
        waitForReceiver(context, 10_000, latch, null);
    }

    @Test
    public void testFlashlight() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        CameraManager cam = context.getSystemService(CameraManager.class);
        String[] cameraIds = cam.getCameraIdList();
        boolean foundFlash = false;
        for (int i = 0; i < cameraIds.length; i++) {
            String id = cameraIds[i];
            if (cam.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                cam.setTorchMode(id, true);
                sleep(500);
                cam.setTorchMode(id, false);
                foundFlash = true;
                break;
            }
        }
        if (!foundFlash) {
            Log.e(TAG, "No flashlight found on device");
        }
    }

    @Test
    public void testForegroundService() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        // The service goes into foreground and exits shortly
        Intent intent = new Intent(context, StatsdCtsForegroundService.class);
        context.startService(intent);
        sleep(500);
        context.stopService(intent);
    }

    @Test
    public void testForegroundServiceAccessAppOp() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        Intent fgsIntent = new Intent(context, StatsdCtsForegroundService.class);
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);

        // No foreground service session
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_COARSE_LOCATION);
        sleep(500);

        // Foreground service session 1
        context.startService(fgsIntent);
        while (!checkIfServiceRunning(context, StatsdCtsForegroundService.class.getName())) {
            sleep(50);
        }
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_CAMERA);
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_FINE_LOCATION);
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_CAMERA);
        startAppOp(appOpsManager, AppOpsManager.OPSTR_RECORD_AUDIO);
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_RECORD_AUDIO);
        startAppOp(appOpsManager, AppOpsManager.OPSTR_CAMERA);
        sleep(500);
        context.stopService(fgsIntent);

        // No foreground service session
        noteAppOp(appOpsManager, AppOpsManager.OPSTR_COARSE_LOCATION);
        sleep(500);

        // TODO(b/149098800): Start fgs a second time and log OPSTR_CAMERA again
    }

    @Test
    public void testAppOps() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);

        String[] opsList = appOpsManager.getOpStrs();

        for (int i = 0; i < opsList.length; i++) {
            String op = opsList[i];
            if (TextUtils.isEmpty(op) || op.startsWith("android:deprecated")) {
                // Operation removed/deprecated
                continue;
            }
            int noteCount = APP_OPS_ENUM_MAP.getOrDefault(op, opsList.length) + 1;
            for (int j = 0; j < noteCount; j++) {
                try {
                    noteAppOp(appOpsManager, opsList[i]);
                } catch (SecurityException e) {
                }
            }
        }
    }

    private void noteAppOp(AppOpsManager aom, String opStr) {
        aom.noteOp(opStr, android.os.Process.myUid(), MY_PACKAGE_NAME, null, "statsdTest");
    }

    private void startAppOp(AppOpsManager aom, String opStr) {
        aom.startOp(opStr, android.os.Process.myUid(), MY_PACKAGE_NAME, null, "statsdTest");
    }

    /** Check if service is running. */
    public boolean checkIfServiceRunning(Context context, String serviceName) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName()) && service.foreground) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testGpsScan() {
        Context context = InstrumentationRegistry.getContext();
        final LocationManager locManager = context.getSystemService(LocationManager.class);
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS provider is not enabled");
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);

        final LocationListener locListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.v(TAG, "onLocationChanged: location has been obtained");
            }

            public void onProviderDisabled(String provider) {
                Log.w(TAG, "onProviderDisabled " + provider);
            }

            public void onProviderEnabled(String provider) {
                Log.w(TAG, "onProviderEnabled " + provider);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.w(TAG, "onStatusChanged " + provider + " " + status);
            }
        };

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Looper.prepare();
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 990, 0,
                        locListener);
                sleep(1_000);
                locManager.removeUpdates(locListener);
                latch.countDown();
                return null;
            }
        }.execute();

        waitForReceiver(context, 59_000, latch, null);
    }

    @Test
    public void testGpsStatus() {
        Context context = InstrumentationRegistry.getContext();
        final LocationManager locManager = context.getSystemService(LocationManager.class);

        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS provider is not enabled");
            return;
        }

        // Time out set to 85 seconds (5 seconds for sleep and a possible 85 seconds if TTFF takes
        // max time which would be around 90 seconds.
        // This is based on similar location cts test timeout values.
        final int TIMEOUT_IN_MSEC = 85_000;
        final int SLEEP_TIME_IN_MSEC = 5_000;

        final CountDownLatch mLatchNetwork = new CountDownLatch(1);

        final LocationListener locListener = location -> {
            Log.v(TAG, "onLocationChanged: location has been obtained");
            mLatchNetwork.countDown();
        };

        // fetch the networklocation first to make sure the ttff is not flaky
        if (locManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
            Log.i(TAG, "Request Network Location updates.");
            locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    0 /* minTime*/,
                    0 /* minDistance */,
                    locListener,
                    Looper.getMainLooper());
        }
        waitForReceiver(context, TIMEOUT_IN_MSEC, mLatchNetwork, null);

        // TTFF could take up to 90 seconds, thus we need to wait till TTFF does occur if it does
        // not occur in the first SLEEP_TIME_IN_MSEC
        final CountDownLatch mLatchTtff = new CountDownLatch(1);

        GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                Log.v(TAG, "Gnss Status Listener Started");
            }

            @Override
            public void onStopped() {
                Log.v(TAG, "Gnss Status Listener Stopped");
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                Log.v(TAG, "Gnss Status Listener Received TTFF");
                mLatchTtff.countDown();
            }

            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                Log.v(TAG, "Gnss Status Listener Received Status Update");
            }
        };

        boolean gnssStatusCallbackAdded = locManager.registerGnssStatusCallback(
                gnssStatusCallback, new Handler(Looper.getMainLooper()));
        if (!gnssStatusCallbackAdded) {
            // Registration of GnssMeasurements listener has failed, this indicates a platform bug.
            Log.e(TAG, "Failed to start gnss status callback");
        }

        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                0,
                0 /* minDistance */,
                locListener,
                Looper.getMainLooper());
        sleep(SLEEP_TIME_IN_MSEC);
        waitForReceiver(context, TIMEOUT_IN_MSEC, mLatchTtff, null);
        locManager.removeUpdates(locListener);
        locManager.unregisterGnssStatusCallback(gnssStatusCallback);
    }

    @Test
    public void testScreenBrightness() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager pm = context.getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP, "StatsdBrightnessTest");
        wl.acquire();
        sleep(500);

        setScreenBrightness(47);
        sleep(500);
        setScreenBrightness(100);
        sleep(500);


        wl.release();
    }

    @Test
    public void testSyncState() throws Exception {

        Context context = InstrumentationRegistry.getContext();
        StatsdAuthenticator.removeAllAccounts(context);
        AccountManager am = context.getSystemService(AccountManager.class);
        CountDownLatch latch = StatsdSyncAdapter.resetCountDownLatch();

        Account account = StatsdAuthenticator.getTestAccount();
        StatsdAuthenticator.ensureTestAccount(context);
        sleep(500);

        // Just force set is syncable.
        ContentResolver.setMasterSyncAutomatically(true);
        sleep(500);
        ContentResolver.setIsSyncable(account, StatsdProvider.AUTHORITY, 1);
        // Wait for the first (automatic) sync to finish
        waitForReceiver(context, 120_000, latch, null);

        //Sleep for 500ms, since we assert each start/stop to be ~500ms apart.
        sleep(500);

        // Request and wait for the second sync to finish
        latch = StatsdSyncAdapter.resetCountDownLatch();
        StatsdSyncAdapter.requestSync(account);
        waitForReceiver(context, 120_000, latch, null);
        StatsdAuthenticator.removeAllAccounts(context);
    }

    @Test
    public void testScheduledJob() throws Exception {
        final ComponentName name = new ComponentName(MY_PACKAGE_NAME,
                StatsdJobService.class.getName());

        Context context = InstrumentationRegistry.getContext();
        JobScheduler js = context.getSystemService(JobScheduler.class);
        assertWithMessage("JobScheduler service not available").that(js).isNotNull();

        JobInfo.Builder builder = new JobInfo.Builder(1, name);
        builder.setOverrideDeadline(0);
        JobInfo job = builder.build();

        CountDownLatch latch = StatsdJobService.resetCountDownLatch();
        js.schedule(job);
        waitForReceiver(context, 5_000, latch, null);
    }

    @Test
    public void testScheduledJob_CancelledJob() throws Exception {
        final ComponentName name = new ComponentName(MY_PACKAGE_NAME,
                StatsdJobService.class.getName());

        Context context = InstrumentationRegistry.getContext();
        JobScheduler js = context.getSystemService(JobScheduler.class);
        assertWithMessage("JobScheduler service not available").that(js).isNotNull();

        JobInfo.Builder builder = new JobInfo.Builder(1, name);
        builder.setMinimumLatency(60_000L);
        JobInfo job = builder.build();

        js.schedule(job);
        js.cancel(1);
    }

    @Test
    public void testScheduledJobPriority() throws Exception {
        final ComponentName name =
                new ComponentName(MY_PACKAGE_NAME, StatsdJobService.class.getName());

        Context context = InstrumentationRegistry.getContext();
        JobScheduler js = context.getSystemService(JobScheduler.class);
        assertWithMessage("JobScheduler service not available").that(js).isNotNull();

        final int[] priorities = {
                JobInfo.PRIORITY_HIGH, JobInfo.PRIORITY_DEFAULT,
                JobInfo.PRIORITY_LOW, JobInfo.PRIORITY_MIN};
        for (int priority : priorities) {
            JobInfo job = new JobInfo.Builder(priority, name)
                    .setOverrideDeadline(0)
                    .setPriority(priority)
                    .build();

            CountDownLatch latch = StatsdJobService.resetCountDownLatch();
            js.schedule(job);
            waitForReceiver(context, 5_000, latch, null);
        }
    }

    @Test
    public void testVibratorState() {
        Context context = InstrumentationRegistry.getContext();
        Vibrator vib = context.getSystemService(Vibrator.class);
        if (vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(
                    500 /* ms */, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        // Sleep so that the app does not get killed.
        sleep(1000);
    }

    @Test
    public void testWakelockState() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager pm = context.getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "StatsdPartialWakelock");
        wl.acquire();
        sleep(500);
        wl.release();
    }

    @Test
    public void testSliceByWakelockState() {
        int uid = Process.myUid();
        int whatAtomId = 9_998;
        int wakelockType = PowerManager.PARTIAL_WAKE_LOCK;
        String tag = "StatsdPartialWakelock";

        Context context = InstrumentationRegistry.getContext();
        PowerManager pm = context.getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(wakelockType, tag);

        wl.acquire();
        sleep(500);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        wl.acquire();
        sleep(500);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        wl.release();
        sleep(500);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        wl.release();
        sleep(500);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
        writeSliceByWakelockStateChangedAtom(whatAtomId, uid, wakelockType, tag);
    }

    private static void writeSliceByWakelockStateChangedAtom(int atomId, int firstUid,
            int field2, String field3) {
        final StatsEvent.Builder builder = StatsEvent.newBuilder()
                .setAtomId(atomId)
                .writeAttributionChain(new int[]{firstUid}, new String[]{"tag1"})
                .writeInt(field2)
                .writeString(field3)
                .usePooledBuffer();

        StatsLog.write(builder.build());
    }

    @Test
    public void testWakelockLoad() {
        final int NUM_THREADS = 16;
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        for (int i = 0; i < NUM_THREADS; i++) {
            Thread t = new Thread(new WakelockLoadTestRunnable("StatsdPartialWakelock" + i, latch));
            t.start();
        }
        waitForReceiver(null, 120_000, latch, null);
    }

    @Test
    public void testWifiLockHighPerf() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        boolean wifiConnected = isWifiConnected(context);
        Assert.assertTrue(
                "Wifi is not connected. The test expects Wifi to be connected before the run",
                wifiConnected);

        WifiManager wm = context.getSystemService(WifiManager.class);
        WifiManager.WifiLock lock =
                wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "StatsdCTSWifiLock");
        lock.acquire();
        sleep(500);
        lock.release();
    }

    @Test
    public void testWifiConnected() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        boolean wifiConnected = isWifiConnected(context);
        Assert.assertTrue(
                "Wifi is not connected. The test expects Wifi to be connected before the run",
                wifiConnected);
    }

    @Test
    public void testWifiMulticastLock() {
        Context context = InstrumentationRegistry.getContext();
        WifiManager wm = context.getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wm.createMulticastLock("StatsdCTSMulticastLock");
        lock.acquire();
        sleep(500);
        lock.release();
    }

    @Test
    /** Does two wifi scans. */
    // TODO: Copied this from BatterystatsValidation but we probably don't need to wait for results.
    public void testWifiScan() {
        Context context = InstrumentationRegistry.getContext();
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        // Sometimes a scan was already running (from a different uid), so the first scan doesn't
        // start when requested. Therefore, additionally wait for whatever scan is currently running
        // to finish, then request a scan again - at least one of these two scans should be
        // attributed to this app.
        for (int i = 0; i < 2; i++) {
            CountDownLatch onReceiveLatch = new CountDownLatch(1);
            BroadcastReceiver receiver = registerReceiver(context, onReceiveLatch, intentFilter);
            context.getSystemService(WifiManager.class).startScan();
            waitForReceiver(context, 60_000, onReceiveLatch, receiver);
        }
    }

    @Test
    public void testWifiReconnect() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        boolean wifiConnected = isWifiConnected(context);
        Assert.assertTrue(
                "Wifi is not connected. The test expects Wifi to be connected before the run",
                wifiConnected);

        wifiDisconnect(context);
        sleep(500);
        wifiReconnect(context);
        sleep(500);
    }

    @Test
    public void testSimpleCpu() {
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            timestamp += i;
        }
        Log.i(TAG, "The answer is " + timestamp);
    }

    @Test
    public void testWriteRawTestAtom() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(context.getPackageName(), 0);
        int[] uids = {1234, appInfo.uid};
        String[] tags = {"tag1", "tag2"};
        byte[] experimentIds = {8, 1, 8, 2, 8, 3}; // Corresponds to 1, 2, 3.

        int[] int32Array = {3, 6};
        long[] int64Array = {1000L, 1002L};
        float[] floatArray = {0.3f, 0.09f};
        String[] stringArray = {"str1", "str2"};
        boolean[] boolArray = {true, false};
        int[] enumArray = {StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__OFF,
                StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__ON};

        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_ATOM_REPORTED, uids, tags, 42,
                Long.MAX_VALUE, 3.14f, "This is a basic test!", false,
                StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__ON, experimentIds, int32Array,
                int64Array, floatArray, stringArray, boolArray, enumArray);

        // All nulls. Should get dropped since cts app is not in the attribution chain.
        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_ATOM_REPORTED, null, null, 0, 0, 0f, null,
                false, StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__ON, null, null, null, null,
                null, null, null);

        // Null tag in attribution chain.
        int[] uids2 = {9999, appInfo.uid};
        String[] tags2 = {"tag9999", null};
        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_ATOM_REPORTED, uids2, tags2, 100,
                Long.MIN_VALUE, -2.5f, "Test null uid", true,
                StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__UNKNOWN, experimentIds, int32Array,
                int64Array, floatArray, stringArray, boolArray, enumArray);

        // Non chained non-null
        StatsLogStatsdCts.write_non_chained(StatsLogStatsdCts.TEST_ATOM_REPORTED, appInfo.uid,
                "tag1", -256, -1234567890L, 42.01f, "Test non chained", true,
                StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__OFF, experimentIds, new int[0],
                new long[0], new float[0], new String[0], new boolean[0], new int[0]);

        // Non chained all null
        StatsLogStatsdCts.write_non_chained(StatsLogStatsdCts.TEST_ATOM_REPORTED, appInfo.uid, null,
                0, 0, 0f, null, true, StatsLogStatsdCts.TEST_ATOM_REPORTED__STATE__OFF, null, null,
                null, null, null, null, null);
    }

    @Test
    public void testWriteExtensionTestAtom() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(context.getPackageName(), 0);
        int[] uids = {1234, appInfo.uid};
        String[] tags = {"tag1", "tag2"};
        byte[] testAtomNestedMsg = {8, 1, 8, 2, 8, 3}; // Corresponds to 1, 2, 3.

        int[] int32Array = {3, 6};
        long[] int64Array = {1000L, 1002L};
        float[] floatArray = {0.3f, 0.09f};
        String[] stringArray = {"str1", "str2"};
        boolean[] boolArray = {true, false};
        int[] enumArray = {StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__OFF,
                StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__ON};

        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED, uids, tags, 42,
                Long.MAX_VALUE, 3.14f, "This is a basic test!", false,
                StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__ON, testAtomNestedMsg,
                int32Array,
                int64Array, floatArray, stringArray, boolArray, enumArray);

        // All nulls. Should get dropped since cts app is not in the attribution chain.
        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED, null, null, 0, 0,
                0f, null,
                false, StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__ON, null, null, null,
                null,
                null, null, null);

        // Null tag in attribution chain.
        int[] uids2 = {9999, appInfo.uid};
        String[] tags2 = {"tag9999", null};
        StatsLogStatsdCts.write(StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED, uids2, tags2, 100,
                Long.MIN_VALUE, -2.5f, "Test null uid", true,
                StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__UNKNOWN, testAtomNestedMsg,
                int32Array,
                int64Array, floatArray, stringArray, boolArray, enumArray);

        // Non chained non-null
        StatsLogStatsdCts.write_non_chained(StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED,
                appInfo.uid,
                "tag1", -256, -1234567890L, 42.01f, "Test non chained", true,
                StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__OFF, testAtomNestedMsg,
                new int[0],
                new long[0], new float[0], new String[0], new boolean[0], new int[0]);

        // Non chained all null
        StatsLogStatsdCts.write_non_chained(StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED,
                appInfo.uid, null,
                0, 0, 0f, null, true, StatsLogStatsdCts.TEST_EXTENSION_ATOM_REPORTED__STATE__OFF,
                null, null,
                null, null, null, null, null);
    }

    /**
     * Bring up and generate some traffic on cellular data connection.
     */
    @Test
    public void testGenerateMobileTraffic() throws Exception {
        final Context context = InstrumentationRegistry.getContext();
        doGenerateNetworkTraffic(context, NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    /**
     * Force poll NetworkStatsService to get most updated network stats from lower layer.
     */
    @Test
    public void testForcePollNetworkStats() throws Exception {
        final Context context = InstrumentationRegistry.getContext();
        final NetworkStatsManager nsm = context.getSystemService(NetworkStatsManager.class);
        try {
            nsm.setPollForce(true);
            // This query is for triggering force poll NetworkStatsService.
            nsm.querySummaryForUser(ConnectivityManager.TYPE_WIFI, null, Long.MIN_VALUE,
                    Long.MAX_VALUE);
        } catch (RemoteException e) {
            Log.e(TAG, "doPollNetworkStats failed with " + e);
        }
    }

    // Constants which are locally used by doGenerateNetworkTraffic.
    private static final int NETWORK_TIMEOUT_MILLIS = 15000;
    private static final String HTTPS_HOST_URL =
            "https://connectivitycheck.gstatic.com/generate_204";

    private void doGenerateNetworkTraffic(@NonNull Context context, int transport)
            throws InterruptedException {
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        final NetworkRequest request = new NetworkRequest.Builder().addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(transport).build();
        final CtsNetUtils.TestNetworkCallback callback = new CtsNetUtils.TestNetworkCallback();

        // Request network, and make http query when the network is available.
        cm.requestNetwork(request, callback);

        // If network is not available, throws IllegalStateException.
        final Network network = callback.waitForAvailable();
        if (network == null) {
            throw new IllegalStateException("network with transport " + transport
                    + " is not available.");
        }

        final long startTime = SystemClock.elapsedRealtime();
        try {
            exerciseRemoteHost(cm, network, new URL(HTTPS_HOST_URL));
            Log.i(TAG, "exerciseRemoteHost successful in " + (SystemClock.elapsedRealtime()
                    - startTime) + " ms");
        } catch (Exception e) {
            Log.e(TAG, "exerciseRemoteHost failed in " + (SystemClock.elapsedRealtime()
                    - startTime) + " ms: " + e);
        } finally {
            cm.unregisterNetworkCallback(callback);
        }
    }

    /**
     * Generate traffic on specified network.
     */
    private void exerciseRemoteHost(@NonNull ConnectivityManager cm, @NonNull Network network,
            @NonNull URL url) throws Exception {
        cm.bindProcessToNetwork(network);
        HttpURLConnection urlc = null;
        try {
            urlc = (HttpURLConnection) network.openConnection(url);
            urlc.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
            urlc.setUseCaches(false);
            urlc.connect();
        } finally {
            if (urlc != null) {
                urlc.disconnect();
            }
        }
    }

    // ------- Helper methods

    /** Puts the current thread to sleep. */
    static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while sleeping", e);
        }
    }

    /** Register receiver to determine when given action is complete. */
    private static BroadcastReceiver registerReceiver(
            Context ctx, CountDownLatch onReceiveLatch, IntentFilter intentFilter) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received broadcast.");
                onReceiveLatch.countDown();
            }
        };
        // Run Broadcast receiver in a different thread since the main thread will wait.
        HandlerThread handlerThread = new HandlerThread("br_handler_thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        ctx.registerReceiver(receiver, intentFilter, null, handler);
        return receiver;
    }

    /**
     * Uses the receiver to wait until the action is complete. ctx and receiver may be null if no
     * receiver is needed to be unregistered.
     */
    private static void waitForReceiver(Context ctx,
            int maxWaitTimeMs, CountDownLatch latch, BroadcastReceiver receiver) {
        try {
            boolean didFinish = latch.await(maxWaitTimeMs, TimeUnit.MILLISECONDS);
            if (didFinish) {
                Log.v(TAG, "Finished performing action");
            } else {
                // This is not necessarily a problem. If we just want to make sure a count was
                // recorded for the request, it doesn't matter if the action actually finished.
                Log.w(TAG, "Did not finish in specified time.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while awaiting action to finish", e);
        }
        if (ctx != null && receiver != null) {
            ctx.unregisterReceiver(receiver);
        }
    }

    private static void setScreenBrightness(int brightness) {
        runShellCommand("settings put system screen_brightness " + brightness);
    }

    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;

    public void wifiDisconnect(Context context) throws Exception {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.disconnect());

        PollingCheck.check(
                "Timed out waiting for Wifi to become disconnected",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> !isWifiConnected(context));
    }

    public void wifiReconnect(Context context) throws Exception {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        ShellIdentityUtils.invokeWithShellPermissions(() -> wifiManager.reconnect());

        PollingCheck.check(
                "Timed out waiting for Wifi to become connected",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> isWifiConnected(context));
    }

    private boolean isWifiConnected(Context context) throws Exception {
        ConnectivityManager connManager = context.getSystemService(ConnectivityManager.class);
        if (connManager == null) {
            return false;
        }

        Network[] networks = connManager.getAllNetworks();
        for (Network network : networks) {
            if (network == null) {
                continue;
            }

            NetworkCapabilities caps = connManager.getNetworkCapabilities(network);
            if (caps == null) {
                continue;
            }

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void testGameState() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        GameManager gameManager = context.getSystemService(GameManager.class);
        gameManager.setGameState(new GameState(true, GameState.MODE_CONTENT, 1, 2));
    }

    @Test
    public void testSetGameMode() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        GameManager gameManager = context.getSystemService(GameManager.class);
        assertNotNull(gameManager);
        assertNotNull(context.getPackageName());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                (gm) -> gm.setGameMode(context.getPackageName(),
                        GameManager.GAME_MODE_PERFORMANCE), "android.permission.MANAGE_GAME_MODE");
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                (gm) -> gm.setGameMode(context.getPackageName(),
                        GameManager.GAME_MODE_BATTERY), "android.permission.MANAGE_GAME_MODE");
    }

    @Test
    public void testUpdateCustomGameModeConfiguration() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        GameManager gameManager = context.getSystemService(GameManager.class);
        assertNotNull(gameManager);
        assertNotNull(context.getPackageName());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                (gm) -> gm.updateCustomGameModeConfiguration(context.getPackageName(),
                        new GameModeConfiguration.Builder()
                                .setScalingFactor(0.5f)
                                .setFpsOverride(30).build()));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                (gm) -> gm.updateCustomGameModeConfiguration(context.getPackageName(),
                        new GameModeConfiguration.Builder()
                                .setScalingFactor(0.9f)
                                .setFpsOverride(60).build()));
    }

    @Test
    public void testCreateHintSession() throws Exception {
        final long targetNs = 16666666L;
        final int firstApiLevel = PropertyUtil.getFirstApiLevel();
        Context context = InstrumentationRegistry.getContext();
        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);

        assertNotNull(phm);

        // If the device does not support ADPF hint session,
        // getPreferredUpdateRateNanos() returns -1.
        // We only test the devices supporting it and will check
        // if assumption fails in PerformanceHintManagerStatsTests#testCreateHintSessionStatsd
        assumeTrue(phm.getPreferredUpdateRateNanos() != -1);

        PerformanceHintManager.Session session =
                phm.createHintSession(new int[]{Process.myPid()}, targetNs);

        if (firstApiLevel < Build.VERSION_CODES.S) {
            assumeNotNull(session);
        } else {
            assertNotNull(session);
        }
    }

    @Test
    public void testMediaDrmAtoms() throws Exception {
        UUID clearKeyUuid = new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);
        byte[] sid = null;
        final int OEM_ERROR = 123;
        final int ERROR_CONTEXT = 456;
        final int ANDROID_U = 14;
        try (MediaDrm drm = new MediaDrm(clearKeyUuid)) {
            if (getClearkeyVersionInt(drm) >= ANDROID_U) {
                drm.setPropertyString("oemError", Integer.toString(OEM_ERROR));
                drm.setPropertyString("errorContext", Integer.toString(ERROR_CONTEXT));
            }
            for (int i = 0; i < 2; i++) {
                // Mock error is set per-session
                drm.setPropertyString("drmErrorTest", "lostState");
                sid = drm.openSession();
                Assert.assertNotNull("null session id", sid);
                try {
                    drm.closeSession(sid);
                } catch (MediaDrm.MediaDrmStateException e) {
                    Log.d(TAG, "expected for lost state");
                }
            }
        }
    }

    private int getClearkeyVersionInt(MediaDrm drm) {
        try {
            return Integer.parseInt(drm.getPropertyString("version"));
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }
}
