package com.android.server.telecom;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.util.SparseArray;

import com.android.internal.util.IndentingPrintWriter;

public interface CallAudioRouteAdapter {
    /** Valid values for msg.what */
    int CONNECT_WIRED_HEADSET = 1;
    int DISCONNECT_WIRED_HEADSET = 2;
    int CONNECT_DOCK = 5;
    int DISCONNECT_DOCK = 6;
    int BLUETOOTH_DEVICE_LIST_CHANGED = 7;
    int BT_ACTIVE_DEVICE_PRESENT = 8;
    int BT_ACTIVE_DEVICE_GONE = 9;
    int BT_DEVICE_ADDED = 10;
    int BT_DEVICE_REMOVED = 11;

    int SWITCH_EARPIECE = 1001;
    int SWITCH_BLUETOOTH = 1002;
    int SWITCH_HEADSET = 1003;
    int SWITCH_SPEAKER = 1004;
    // Wired headset, earpiece, or speakerphone, in that order of precedence.
    int SWITCH_BASELINE_ROUTE = 1005;

    // Messages denoting that the speakerphone was turned on/off. Used to update state when we
    // weren't the ones who turned it on/off
    int SPEAKER_ON = 1006;
    int SPEAKER_OFF = 1007;

    // Messages denoting that the streaming route switch request was sent.
    int STREAMING_FORCE_ENABLED = 1008;
    int STREAMING_FORCE_DISABLED = 1009;

    int USER_SWITCH_EARPIECE = 1101;
    int USER_SWITCH_BLUETOOTH = 1102;
    int USER_SWITCH_HEADSET = 1103;
    int USER_SWITCH_SPEAKER = 1104;
    int USER_SWITCH_BASELINE_ROUTE = 1105;

    int UPDATE_SYSTEM_AUDIO_ROUTE = 1201;

    // These three messages indicate state changes that come from BluetoothRouteManager.
    // They may be triggered by the BT stack doing something on its own or they may be sent after
    // we request that the BT stack do something. Any logic for these messages should take into
    // account the possibility that the event indicated has already been processed (i.e. handling
    // should be idempotent).
    int BT_AUDIO_DISCONNECTED = 1301;
    int BT_AUDIO_CONNECTED = 1302;
    int BT_AUDIO_PENDING = 1303;

    int MUTE_ON = 3001;
    int MUTE_OFF = 3002;
    int TOGGLE_MUTE = 3003;
    int MUTE_EXTERNALLY_CHANGED = 3004;

    int SWITCH_FOCUS = 4001;

    // Used in testing to execute verifications. Not compatible with subsessions.
    int RUN_RUNNABLE = 9001;

    // Used for PendingAudioRoute to notify audio switch success
    int EXIT_PENDING_ROUTE = 10001;
    // Used for PendingAudioRoute to notify audio switch timeout
    int PENDING_ROUTE_TIMEOUT = 10002;
    // Used for PendingAudioRoute to notify audio switch failed
    int PENDING_ROUTE_FAILED = 10003;

    /** Valid values for mAudioFocusType */
    int NO_FOCUS = 1;
    int ACTIVE_FOCUS = 2;
    int RINGING_FOCUS = 3;

    /** Valid arg for BLUETOOTH_DEVICE_LIST_CHANGED */
    int DEVICE_CONNECTED = 1;
    int DEVICE_DISCONNECTED = 2;

    SparseArray<String> MESSAGE_CODE_TO_NAME = new SparseArray<String>() {{
        put(CONNECT_WIRED_HEADSET, "CONNECT_WIRED_HEADSET");
        put(DISCONNECT_WIRED_HEADSET, "DISCONNECT_WIRED_HEADSET");
        put(CONNECT_DOCK, "CONNECT_DOCK");
        put(DISCONNECT_DOCK, "DISCONNECT_DOCK");
        put(BLUETOOTH_DEVICE_LIST_CHANGED, "BLUETOOTH_DEVICE_LIST_CHANGED");
        put(BT_ACTIVE_DEVICE_PRESENT, "BT_ACTIVE_DEVICE_PRESENT");
        put(BT_ACTIVE_DEVICE_GONE, "BT_ACTIVE_DEVICE_GONE");
        put(BT_DEVICE_ADDED, "BT_DEVICE_ADDED");
        put(BT_DEVICE_REMOVED, "BT_DEVICE_REMOVED");

        put(SWITCH_EARPIECE, "SWITCH_EARPIECE");
        put(SWITCH_BLUETOOTH, "SWITCH_BLUETOOTH");
        put(SWITCH_HEADSET, "SWITCH_HEADSET");
        put(SWITCH_SPEAKER, "SWITCH_SPEAKER");
        put(SWITCH_BASELINE_ROUTE, "SWITCH_BASELINE_ROUTE");
        put(SPEAKER_ON, "SPEAKER_ON");
        put(SPEAKER_OFF, "SPEAKER_OFF");

        put(STREAMING_FORCE_ENABLED, "STREAMING_FORCE_ENABLED");
        put(STREAMING_FORCE_DISABLED, "STREAMING_FORCE_DISABLED");

        put(USER_SWITCH_EARPIECE, "USER_SWITCH_EARPIECE");
        put(USER_SWITCH_BLUETOOTH, "USER_SWITCH_BLUETOOTH");
        put(USER_SWITCH_HEADSET, "USER_SWITCH_HEADSET");
        put(USER_SWITCH_SPEAKER, "USER_SWITCH_SPEAKER");
        put(USER_SWITCH_BASELINE_ROUTE, "USER_SWITCH_BASELINE_ROUTE");

        put(UPDATE_SYSTEM_AUDIO_ROUTE, "UPDATE_SYSTEM_AUDIO_ROUTE");

        put(BT_AUDIO_DISCONNECTED, "BT_AUDIO_DISCONNECTED");
        put(BT_AUDIO_CONNECTED, "BT_AUDIO_CONNECTED");
        put(BT_AUDIO_PENDING, "BT_AUDIO_PENDING");

        put(MUTE_ON, "MUTE_ON");
        put(MUTE_OFF, "MUTE_OFF");
        put(TOGGLE_MUTE, "TOGGLE_MUTE");
        put(MUTE_EXTERNALLY_CHANGED, "MUTE_EXTERNALLY_CHANGED");

        put(SWITCH_FOCUS, "SWITCH_FOCUS");

        put(RUN_RUNNABLE, "RUN_RUNNABLE");

        put(EXIT_PENDING_ROUTE, "EXIT_PENDING_ROUTE");
    }};

    void initialize();
    void sendMessageWithSessionInfo(int message);
    void sendMessageWithSessionInfo(int message, int arg);
    void sendMessageWithSessionInfo(int message, int arg, String data);
    void sendMessageWithSessionInfo(int message, int arg, BluetoothDevice bluetoothDevice);
    void sendMessage(int message, Runnable r);
    void setCallAudioManager(CallAudioManager callAudioManager);
    CallAudioState getCurrentCallAudioState();
    boolean isHfpDeviceAvailable();
    Handler getAdapterHandler();
    void dump(IndentingPrintWriter pw);
}
