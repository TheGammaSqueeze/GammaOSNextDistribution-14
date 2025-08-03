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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;
import com.google.common.base.Objects;

import java.util.List;

public class BluetoothHeadsetFragment extends Fragment {
    private static final String TAG = "CAR.BLUETOOTH.KS";
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mPickedDevice;

    TextView mPickedDeviceText;
    Button mDevicePicker;
    Button mConnect;
    Button mScoConnect;
    Button mScoDisconnect;
    Button mHoldCall;
    Button mStartOutgoingCall;
    Button mEndOutgoingCall;
    EditText mOutgoingPhoneNumber;

    BluetoothHeadsetClient mHfpClientProfile;
    InCallServiceImpl mInCallService;
    ServiceConnection mInCallServiceConnection;

    // Intent for picking a Bluetooth device
    public static final String DEVICE_PICKER_ACTION =
        "android.bluetooth.devicepicker.action.LAUNCH";

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bluetooth_headset, container, false);

        mPickedDeviceText = (TextView) v.findViewById(R.id.bluetooth_device);
        mDevicePicker = (Button) v.findViewById(R.id.bluetooth_pick_device);
        mConnect = (Button) v.findViewById(R.id.bluetooth_headset_connect);
        mScoConnect = (Button) v.findViewById(R.id.bluetooth_sco_connect);
        mScoDisconnect = (Button) v.findViewById(R.id.bluetooth_sco_disconnect);
        mHoldCall = (Button) v.findViewById(R.id.bluetooth_hold_call);
        mStartOutgoingCall = (Button) v.findViewById(R.id.bluetooth_start_outgoing_call);
        mEndOutgoingCall = (Button) v.findViewById(R.id.bluetooth_end_outgoing_call);
        mOutgoingPhoneNumber = (EditText) v.findViewById(R.id.bluetooth_outgoing_phone_number);

        checkPermissions();
        setUpInCallServiceImpl();

        // Connect profile
        mConnect.setOnClickListener(view -> connect());

        // Connect SCO
        mScoConnect.setOnClickListener(view -> connectSco());

        // Disconnect SCO
        mScoDisconnect.setOnClickListener(view -> disconnectSco());

        // Place the current call on hold
        mHoldCall.setOnClickListener(view -> holdCall());

        // Start an outgoing call
        mStartOutgoingCall.setOnClickListener(view -> startCall());

        // Stop an outgoing call
        mEndOutgoingCall.setOnClickListener(view -> stopCall());

        return v;
    }

    private void checkPermissions() {
        if (!BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_CONNECT)) {
            BluetoothPermissionChecker.requestPermission(Manifest.permission.BLUETOOTH_CONNECT,
                    this,
                    this::setDevicePickerButtonClickable,
                    () -> {
                        setDevicePickerButtonUnclickable();
                        Toast.makeText(getContext(),
                                "Device picker can't run without BLUETOOTH_CONNECT permission. "
                                        + "(You can change permissions in Settings.)",
                                Toast.LENGTH_SHORT).show();
                    }
            );
        }
    }

    private void setUpInCallServiceImpl() {
        mInCallServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "InCallServiceImpl is connected");
                mInCallService = ((InCallServiceImpl.LocalBinder) service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "InCallServiceImpl is disconnected");
                mInCallService = null;
            }
        };

        Intent intent = new Intent(this.getContext(), InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        this.getContext().bindService(intent, mInCallServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setDevicePickerButtonClickable() {
        mDevicePicker.setClickable(true);

        // Pick a bluetooth device
        mDevicePicker.setOnClickListener(view -> launchDevicePicker());
    }

    private void setDevicePickerButtonUnclickable() {
        mDevicePicker.setClickable(false);
    }

    void launchDevicePicker() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        getContext().registerReceiver(mPickerReceiver, filter);

        Intent intent = new Intent(DEVICE_PICKER_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        getContext().startActivity(intent);
    }

    void connect() {
        if (mPickedDevice == null) {
            Log.w(TAG, "Device null when trying to connect sco!");
            return;
        }

        // Check if we have the proxy and connect the device.
        if (mHfpClientProfile == null) {
            Log.w(TAG, "HFP Profile proxy not available, cannot connect sco to " + mPickedDevice);
            return;
        }
        mHfpClientProfile.setConnectionPolicy(mPickedDevice,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mPickedDevice.connect();
    }

    void connectSco() {
        Call call = getFirstActiveCall();
        if (call != null) {
            // TODO(b/206035301): Use the public version of this string
            call.sendCallEvent("com.android.bluetooth.hfpclient.SCO_CONNECT",
                    /* extras= */ null);
        }
    }

    void disconnectSco() {
        Call call = getFirstActiveCall();
        if (call != null) {
            // TODO(b/206035301): Use the public version of this string
            call.sendCallEvent("com.android.bluetooth.hfpclient.SCO_DISCONNECT",
                    /* extras= */ null);
        }
    }

    void holdCall() {
        Call call = getFirstActiveCall();
        if (call != null) {
            call.hold();
        }
    }

    void startCall() {
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        if (!Objects.equal(telecomManager.getDefaultDialerPackage(),
                getContext().getPackageName())) {
            Log.w(TAG, "Kitchen Sink cannot manage phone calls unless it is the default "
                    + "dialer app. This can be set in Settings>Apps>Default apps");
        }

        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, mOutgoingPhoneNumber.getText().toString(),
                /* fragment= */ null);
        telecomManager.placeCall(uri, /* extras= */ null);
    }

    void stopCall() {
        Call call = getFirstActiveCall();
        if (call != null) {
            call.disconnect();
        }
    }

    private Call getFirstActiveCall() {
        if (mInCallService == null) {
            Log.w(TAG, "InCallServiceImpl was not connected");
            return null;
        }

        List<Call> calls = mInCallService.getCalls();
        if (calls == null || calls.size() == 0) {
            Log.w(TAG, "No calls are currently connected");
            return null;
        }

        return mInCallService.getCalls().get(0);
    }


    private final BroadcastReceiver mPickerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.v(TAG, "mPickerReceiver got " + action);

            if (BluetoothDevicePicker.ACTION_DEVICE_SELECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) {
                    Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                mPickedDevice = device;
                String text = device.getName() == null ?
                    device.getAddress() : device.getName() + " " + device.getAddress();
                mPickedDeviceText.setText(text);

                // The receiver can now be disabled.
                getContext().unregisterReceiver(mPickerReceiver);
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(
            getContext(), new ProfileServiceListener(), BluetoothProfile.HEADSET_CLIENT);

        if (BluetoothPermissionChecker.isPermissionGranted(
                getActivity(), Manifest.permission.BLUETOOTH_CONNECT)) {
            setDevicePickerButtonClickable();
        } else {
            setDevicePickerButtonUnclickable();
        }
    }

    @Override
    public void onDestroy() {
        getContext().unbindService(mInCallServiceConnection);
        super.onDestroy();
    }

    class ProfileServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "Proxy connected for profile: " + profile);
            switch (profile) {
                case BluetoothProfile.HEADSET_CLIENT:
                    mHfpClientProfile = (BluetoothHeadsetClient) proxy;
                    break;
                default:
                    Log.w(TAG, "onServiceConnected not supported profile: " + profile);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "Proxy disconnected for profile: " + profile);
            switch (profile) {
                case BluetoothProfile.HEADSET_CLIENT:
                    mHfpClientProfile = null;
                    break;
                default:
                    Log.w(TAG, "onServiceDisconnected not supported profile: " + profile);
            }
        }
    }
}
