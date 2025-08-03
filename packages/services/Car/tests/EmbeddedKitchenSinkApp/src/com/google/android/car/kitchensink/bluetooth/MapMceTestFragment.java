/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.car.kitchensink.bluetooth;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MapMceTestFragment extends Fragment {
    static final String REPLY_MESSAGE_TO_SEND = "I am currently driving.";
    static final String NEW_MESSAGE_TO_SEND_SHORT = "This is a new message.";
    static final String NEW_MESSAGE_TO_SEND_LONG = "Lorem ipsum dolor sit amet, consectetur "
            + "adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna "
            + "aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi "
            + "ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in "
            + "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint "
            + "occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim "
            + "id est laborum.\n\nCurabitur pretium tincidunt lacus. Nulla gravida orci a odio. "
            + "Nullam varius, turpis et commodo pharetra, est eros bibendum elit, nec luctus "
            + "magna felis sollicitudin mauris. Integer in mauris eu nibh euismod gravida. Duis "
            + "ac tellus et risus vulputate vehicula. Donec lobortis risus a elit. Etiam tempor. "
            + "Ut ullamcorper, ligula eu tempor congue, eros est euismod turpis, id tincidunt "
            + "sapien risus a quam. Maecenas fermentum consequat mi. Donec fermentum. "
            + "Pellentesque malesuada nulla a mi. Duis sapien sem, aliquet nec, commodo eget, "
            + "consequat quis, neque. Aliquam faucibus, elit ut dictum aliquet, felis nisl "
            + "adipiscing sapien, sed malesuada diam lacus eget erat. Cras mollis scelerisque "
            + "nunc. Nullam arcu. Aliquam consequat. Curabitur augue lorem, dapibus quis, "
            + "laoreet et, pretium ac, nisi. Aenean magna nisl, mollis quis, molestie eu, "
            + "feugiat in, orci. In hac habitasse platea dictumst.\n\nLorem ipsum dolor sit "
            + "amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et "
            + "dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco "
            + "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in "
            + "reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. "
            + "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia "
            + "deserunt mollit anim id est laborum.\n\nCurabitur pretium tincidunt lacus. Nulla "
            + "gravida orci a odio. Nullam varius, turpis et commodo pharetra, est eros bibendum "
            + "elit, nec luctus magna felis sollicitudin mauris. Integer in mauris eu nibh "
            + "euismod gravida. Duis ac tellus et risus vulputate vehicula. Donec lobortis risus "
            + "a elit. Etiam tempor. Ut ullamcorper, ligula eu tempor congue, eros est euismod "
            + "turpis, id tincidunt sapien risus a quam. Maecenas fermentum consequat mi. Donec "
            + "fermentum. Pellentesque malesuada nulla a mi. Duis sapien sem, aliquet nec, "
            + "commodo eget, consequat quis, neque. Aliquam faucibus, elit ut dictum aliquet, "
            + "felis nisl adipiscing sapien, sed malesuada diam lacus eget erat. Cras mollis "
            + "scelerisque nunc. Nullam arcu. Aliquam consequat. Curabitur augue lorem, dapibus "
            + "quis, laoreet et, pretium ac, nisi. Aenean magna nisl, mollis quis, molestie eu, "
            + "feugiat in, orci. In hac habitasse platea dictumst.";
    private static final int SEND_NEW_SMS_SHORT = 1;
    private static final int SEND_NEW_SMS_LONG = 2;
    private static final int SEND_NEW_MMS_SHORT = 3;
    private static final int SEND_NEW_MMS_LONG = 4;
    private int mSendNewMsgCounter = 0;
    private static final String TAG = "CAR.BLUETOOTH.KS";
    private static final String ACTION_MESSAGE_SENT_SUCCESSFULLY =
            "com.google.android.car.kitchensink.bluetooth.MESSAGE_SENT_SUCCESSFULLY";
    private static final String ACTION_MESSAGE_DELIVERED_SUCCESSFULLY =
            "com.google.android.car.kitchensink.bluetooth.MESSAGE_DELIVERED_SUCCESSFULLY";
    // {@link BluetoothMapClient.ACTION_MESSAGE_RECEIVED} is a hidden API.
    private static final String MAP_CLIENT_ACTION_MESSAGE_RECEIVED =
            "android.bluetooth.mapmce.profile.action.MESSAGE_RECEIVED";
    // {@link BluetoothMapClient.EXTRA_SENDER_CONTACT_URI} is a hidden API.
    private static final String MAP_CLIENT_EXTRA_SENDER_CONTACT_URI =
            "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_URI";
    // {@link BluetoothMapClient.EXTRA_SENDER_CONTACT_NAME} is a hidden API.
    private static final String MAP_CLIENT_EXTRA_SENDER_CONTACT_NAME =
            "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_NAME";
    // {@link BluetoothMapClient.EXTRA_MESSAGE_TIMESTAMP} is a hidden API.
    private static final String MAP_CLIENT_EXTRA_MESSAGE_TIMESTAMP =
            "android.bluetooth.mapmce.profile.extra.MESSAGE_TIMESTAMP";
    // {@link BluetoothMapClient.EXTRA_MESSAGE_READ_STATUS} is a hidden API.
    private static final String MAP_CLIENT_EXTRA_MESSAGE_READ_STATUS =
            "android.bluetooth.mapmce.profile.extra.MESSAGE_READ_STATUS";
    private static final int SEND_SMS_PERMISSIONS_REQUEST = 1;
    BluetoothMapClient mMapProfile;
    BluetoothAdapter mBluetoothAdapter;
    Button mDevicePicker;
    Button mDeviceDisconnect;
    TextView mMessage;
    EditText mOriginator;
    EditText mSmsTelNum;
    TextView mOriginatorDisplayName;
    CheckBox mSent;
    CheckBox mDelivered;
    TextView mBluetoothDevice;
    PendingIntent mSentIntent;
    PendingIntent mDeliveredIntent;
    NotificationReceiver mTransmissionStatusReceiver;
    Object mLock = new Object();
    private Intent mSendIntent;
    private Intent mDeliveryIntent;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.sms_received, container, false);

        if (!BluetoothPermissionChecker.isPermissionGranted(getActivity(),
                Manifest.permission.BLUETOOTH_CONNECT)) {
            BluetoothPermissionChecker.requestPermission(Manifest.permission.BLUETOOTH_CONNECT,
                    this,
                    this::registerMapServiceListenerAndNotificationReceiver,
                    () -> {
                    Toast.makeText(getContext(),
                        "Connected devices can't be detected without BLUETOOTH_CONNECT "
                                + "permission. (You can change permissions in Settings.)",
                        Toast.LENGTH_SHORT).show();
                });
        }

        Button reply = (Button) v.findViewById(R.id.reply);
        mBluetoothDevice = (TextView) v.findViewById(R.id.bluetoothDevice);
        Button sendNewMsgShort = (Button) v.findViewById(R.id.sms_new_message);
        Button sendNewMsgLong = (Button) v.findViewById(R.id.mms_new_message);
        Button resetSendNewMsgCounter = (Button) v.findViewById(R.id.reset_message_counter);
        mSmsTelNum = (EditText) v.findViewById(R.id.sms_tel_num);
        mOriginator = (EditText) v.findViewById(R.id.messageOriginator);
        mOriginatorDisplayName = (TextView) v.findViewById(R.id.messageOriginatorDisplayName);
        mSent = (CheckBox) v.findViewById(R.id.sent_checkbox);
        mDelivered = (CheckBox) v.findViewById(R.id.delivered_checkbox);
        mSendIntent = new Intent(ACTION_MESSAGE_SENT_SUCCESSFULLY);
        mDeliveryIntent = new Intent(ACTION_MESSAGE_DELIVERED_SUCCESSFULLY);
        mMessage = (TextView) v.findViewById(R.id.messageContent);
        mDevicePicker = (Button) v.findViewById(R.id.bluetooth_pick_device);
        mDeviceDisconnect = (Button) v.findViewById(R.id.bluetooth_disconnect_device);

        //TODO add manual entry option for phone number
        reply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage(Collections.singleton(Uri.parse(mOriginator.getText().toString())),
                        REPLY_MESSAGE_TO_SEND);
            }
        });

        sendNewMsgShort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendNewMsgOnClick(SEND_NEW_SMS_SHORT);
            }
        });

        sendNewMsgLong.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendNewMsgOnClick(SEND_NEW_MMS_LONG);
            }
        });

        resetSendNewMsgCounter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSendNewMsgCounter = 0;
                Toast.makeText(getContext(), "Counter reset to zero.", Toast.LENGTH_SHORT).show();
            }
        });

        // Pick a bluetooth device
        mDevicePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchDevicePicker();
            }
        });
        mDeviceDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnectDevice(mBluetoothDevice.getText().toString());
            }
        });

        return v;
    }

    void launchDevicePicker() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        getContext().registerReceiver(mPickerReceiver, filter);

        Intent intent = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        getContext().startActivity(intent);
    }

    void disconnectDevice(String device) {
        try {
            // {@link BluetoothMapClient#disconnect} is a hidden API.
            // {@link BluetoothMapClient#setConnectionPolicy} is the new method for connecting
            // and disconnecting a profile.
            mMapProfile.setConnectionPolicy(mBluetoothAdapter.getRemoteDevice(device),
                    BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to disconnect from " + device, e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (BluetoothPermissionChecker.isPermissionGranted(getActivity(),
                Manifest.permission.BLUETOOTH_CONNECT)) {
            registerMapServiceListenerAndNotificationReceiver();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mTransmissionStatusReceiver != null) {
            getContext().unregisterReceiver(mTransmissionStatusReceiver);
            mTransmissionStatusReceiver = null;
        }
    }

    private void registerMapServiceListenerAndNotificationReceiver() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(getContext(), new MapServiceListener(),
                BluetoothProfile.MAP_CLIENT);

        mTransmissionStatusReceiver = new NotificationReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MESSAGE_SENT_SUCCESSFULLY);
        intentFilter.addAction(ACTION_MESSAGE_DELIVERED_SUCCESSFULLY);
        intentFilter.addAction(MAP_CLIENT_ACTION_MESSAGE_RECEIVED);
        getContext().registerReceiver(mTransmissionStatusReceiver, intentFilter,
                Context.RECEIVER_NOT_EXPORTED);

        intentFilter = new IntentFilter(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED);
        getContext().registerReceiver(mTransmissionStatusReceiver, intentFilter);
    }

    private void sendNewMsgOnClick(int msgType) {
        String messageToSend = "";
        switch (msgType) {
            case SEND_NEW_SMS_SHORT:
                messageToSend = NEW_MESSAGE_TO_SEND_SHORT;
                break;
            case SEND_NEW_MMS_LONG:
                messageToSend = NEW_MESSAGE_TO_SEND_LONG;
                break;
            default:
                break;
        }
        String s = mSmsTelNum.getText().toString();
        Toast.makeText(getContext(), "sending msg to " + s, Toast.LENGTH_SHORT).show();
        HashSet<Uri> uris = new HashSet<Uri>();
        Uri.Builder builder = new Uri.Builder();
        for (String telNum : s.split(",")) {
            uris.add(builder.path(telNum).scheme(PhoneAccount.SCHEME_TEL).build());
        }
        sendMessage(uris, Integer.toString(mSendNewMsgCounter) + ":  " + messageToSend);
        mSendNewMsgCounter += 1;
    }

    private void sendMessage(Collection recipients, String message) {
        if (getActivity().checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG,"Don't have SMS permission in kitchesink app. Requesting it");
            getActivity().requestPermissions(new String[]{Manifest.permission.SEND_SMS},
                    SEND_SMS_PERMISSIONS_REQUEST);
            Toast.makeText(getContext(), "Try again after granting SEND_SMS perm!",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        synchronized (mLock) {
            BluetoothDevice remoteDevice;
            try {
                remoteDevice = mBluetoothAdapter.getRemoteDevice(
                        mBluetoothDevice.getText().toString());
            } catch (java.lang.IllegalArgumentException e) {
                Log.e(TAG, e.toString());
                return;
            }
            mSent.setChecked(false);
            mDelivered.setChecked(false);
            if (mMapProfile != null) {
                Log.d(TAG, "Sending reply");
                if (recipients == null) {
                    Log.d(TAG, "Recipients is null");
                    return;
                }
                if (mBluetoothDevice == null) {
                    Log.d(TAG, "BluetoothDevice is null");
                    return;
                }

                mSentIntent = PendingIntent.getBroadcast(getContext(), 0, mSendIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                mDeliveredIntent = PendingIntent.getBroadcast(getContext(), 0, mDeliveryIntent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                Log.d(TAG,"Sending message in kitchesink app: " + message);
                mMapProfile.sendMessage(
                        remoteDevice,
                        recipients, message, mSentIntent, mDeliveredIntent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult reqCode=" + requestCode);
        if (SEND_SMS_PERMISSIONS_REQUEST == requestCode) {
            for (int i=0; i<permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i] == Manifest.permission.SEND_SMS) {
                        Log.d(TAG, "Got the SEND_SMS perm");
                        return;
                    }
                }
            }
        }
    }

    class MapServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            synchronized (mLock) {
                mMapProfile = (BluetoothMapClient) proxy;
                List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                if (connectedDevices.size() > 0) {
                    mBluetoothDevice.setText(connectedDevices.get(0).getAddress());
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            synchronized (mLock) {
                mMapProfile = null;
            }
        }
    }

    private class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            synchronized (mLock) {
                if (action.equals(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED)) {
                    if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0)
                            == BluetoothProfile.STATE_CONNECTED) {
                        mBluetoothDevice.setText(((BluetoothDevice) intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE)).getAddress());
                    } else if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0)
                            == BluetoothProfile.STATE_DISCONNECTED) {
                        mBluetoothDevice.setText("Disconnected");
                    }
                } else if (Objects.equals(action, ACTION_MESSAGE_SENT_SUCCESSFULLY)) {
                    mSent.setChecked(true);
                } else if (Objects.equals(action, ACTION_MESSAGE_DELIVERED_SUCCESSFULLY)) {
                    mDelivered.setChecked(true);
                } else if (Objects.equals(action, MAP_CLIENT_ACTION_MESSAGE_RECEIVED)) {
                    String senderUri =
                            intent.getStringExtra(MAP_CLIENT_EXTRA_SENDER_CONTACT_URI);
                    if (senderUri == null) {
                        senderUri = "<null>";
                    }

                    String senderName = intent.getStringExtra(
                            MAP_CLIENT_EXTRA_SENDER_CONTACT_NAME);
                    if (senderName == null) {
                        senderName = "<null>";
                    }
                    Date msgTimestamp = new Date(intent.getLongExtra(
                            MAP_CLIENT_EXTRA_MESSAGE_TIMESTAMP,
                            System.currentTimeMillis()));
                    boolean msgReadStatus = intent.getBooleanExtra(
                            MAP_CLIENT_EXTRA_MESSAGE_READ_STATUS, false);
                    String msgText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT);
                    String msg = "[" + msgTimestamp + "] " + "("
                            + (msgReadStatus ? "READ" : "UNREAD") + ") " + msgText;
                    mMessage.setText(msg);
                    mOriginator.setText(senderUri);
                    mOriginatorDisplayName.setText(senderName);
                }
            }
        }
    }

    private final BroadcastReceiver mPickerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.v(TAG, "mPickerReceiver got " + action);

            if (Objects.equals(action, BluetoothDevicePicker.ACTION_DEVICE_SELECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.v(TAG, "mPickerReceiver got " + device);
                if (device == null) {
                    Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                // {@link BluetoothMapClient#connect} is a hidden API.
                // {@link BluetoothMapClient#setConnectionPolicy} is the new method for connecting
                // and disconnecting a profile.
                mMapProfile.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);

                // The receiver can now be disabled.
                getContext().unregisterReceiver(mPickerReceiver);
            }
        }
    };
}
