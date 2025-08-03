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

package com.google.android.car.multidisplaytest.occupantconnection;

import static com.google.android.car.multidisplaytest.occupantconnection.Constants.SEEK_BAR_RECEIVER_ID;
import static com.google.android.car.multidisplaytest.occupantconnection.Constants.TEXT_RECEIVER_ID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.occupantconnection.CarOccupantConnectionManager;
import android.car.occupantconnection.CarOccupantConnectionManager.ConnectionRequestCallback;
import android.car.occupantconnection.Payload;
import android.os.Bundle;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.car.multidisplaytest.R;


public class SenderFragment extends Fragment {

    private static final int STATE_NOT_CONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // The member variables are accessed by the main thread only, so there is no multi-thread issue.
    private String mTag;
    private CarOccupantConnectionManager mOccupantConnectionManager;
    private OccupantZoneInfo mReceiverZone;
    private FragmentActivity mActivity;
    private int mConnectionState;

    private final Car.CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            Slog.e(mTag, "Disconnect from Car Service");
            mOccupantConnectionManager = null;
            return;
        }
        Slog.v(mTag, "Connected to Car Service");
        mOccupantConnectionManager = car.getCarManager(CarOccupantConnectionManager.class);
    };

    private final ConnectionRequestCallback mRequestCallback = new ConnectionRequestCallback() {
        @Override
        public void onConnected(OccupantZoneInfo receiverZone) {
            Toast.makeText(mActivity, "Connected", Toast.LENGTH_SHORT).show();
            mConnectionState = STATE_CONNECTED;
        }

        @Override
        public void onFailed(OccupantZoneInfo receiverZone, int connectionError) {
            Toast.makeText(mActivity, "Connection request failed", Toast.LENGTH_SHORT).show();
            mConnectionState = STATE_NOT_CONNECTED;
        }

        @Override
        public void onDisconnected(OccupantZoneInfo receiverZone) {
            Toast.makeText(mActivity, "Disconnected by the receiver", Toast.LENGTH_SHORT).show();
            mConnectionState = STATE_NOT_CONNECTED;
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Car.createCar(getContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mCarServiceLifecycleListener);
        mActivity = getActivity();
        mTag = "OccupantConnection##" + mActivity.getUserId();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mReceiverZone = getArguments().getParcelable(Constants.KEY_OCCUPANT_ZONE);
        View root = inflater.inflate(R.layout.sender_fragment, container, /* attachToRoot= */false);

        Button backButtton = root.findViewById(R.id.back);
        backButtton.setOnClickListener(v -> {
            Fragment occupantConnectionFragment = new OccupantConnectionFragment();
            mActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_content, occupantConnectionFragment)
                    .commit();
        });

        Button requestConnectionButton = root.findViewById(R.id.request_connection);
        requestConnectionButton.setOnClickListener(v -> {
            if (mOccupantConnectionManager == null) {
                Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mConnectionState == STATE_CONNECTING) {
                Toast.makeText(mActivity, "Already requested a connection",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mConnectionState == STATE_CONNECTED) {
                Toast.makeText(mActivity, "Already connected",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mOccupantConnectionManager.requestConnection(mReceiverZone, mActivity.getMainExecutor(),
                    mRequestCallback);
            mConnectionState = STATE_CONNECTING;
            Slog.d(mTag, "Requesting connection to " + mReceiverZone);
        });

        Button cancelConnectionButton = root.findViewById(R.id.cancel_connection);
        cancelConnectionButton.setOnClickListener(v -> {
            if (mOccupantConnectionManager == null) {
                Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mConnectionState != STATE_CONNECTING) {
                Toast.makeText(mActivity, "No pending connection request to cancel",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mOccupantConnectionManager.cancelConnection(mReceiverZone);
            mConnectionState = STATE_NOT_CONNECTED;
            Slog.d(mTag, "Connection request was canceled by us");
        });

        Button disconnectButton = root.findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(v -> {
            if (mOccupantConnectionManager == null) {
                Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mConnectionState != STATE_CONNECTED) {
                Toast.makeText(mActivity, "Can't disconnect because it was not connected",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mOccupantConnectionManager.disconnect(mReceiverZone);
            mConnectionState = STATE_NOT_CONNECTED;
            Slog.d(mTag, "Disconnected by us");
        });

        Button isConnectedButton = root.findViewById(R.id.is_connected);
        isConnectedButton.setOnClickListener(v -> {
            if (mOccupantConnectionManager == null) {
                Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            boolean isConnected = mOccupantConnectionManager.isConnected(mReceiverZone);
            isConnectedButton.setText("is connected? " + isConnected);
        });

        EditText editText = root.findViewById(R.id.edit_text);

        Button sendTextButton = root.findViewById(R.id.send_text);
        sendTextButton.setOnClickListener(v -> {
            if (mOccupantConnectionManager == null) {
                Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mConnectionState != STATE_CONNECTED) {
                Toast.makeText(mActivity, "Can't send text because it was not connected",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String textToSend = editText.getText().toString();
            Payload payload = PayloadUtils.createPayload(TEXT_RECEIVER_ID, textToSend);
            try {
                mOccupantConnectionManager.sendPayload(mReceiverZone, payload);
                Slog.d(mTag, "Message [" + textToSend + "] has been sent to " + mReceiverZone);
            } catch (CarOccupantConnectionManager.PayloadTransferException e) {
                Toast.makeText(mActivity, "Failed to send the message",
                        Toast.LENGTH_SHORT).show();
                Slog.e(mTag, "Failed to send message [" + textToSend + "] to " + mReceiverZone);
            }
            payload.close();
        });

        SeekBar seekBar = root.findViewById(R.id.seek_bar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mOccupantConnectionManager == null) {
                    Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mConnectionState != STATE_CONNECTED) {
                    Toast.makeText(mActivity,
                            "Can't send the progress because it was not connected",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Payload payload = PayloadUtils.createPayload(SEEK_BAR_RECEIVER_ID, progress);
                try {
                    mOccupantConnectionManager.sendPayload(mReceiverZone, payload);
                    Slog.d(mTag, "Progress " + progress + " has been sent to " + mReceiverZone);
                } catch (CarOccupantConnectionManager.PayloadTransferException e) {
                    Toast.makeText(mActivity, "Failed to send the progress",
                            Toast.LENGTH_SHORT).show();
                    Slog.e(mTag, "Failed to send progress " + progress + " to " + mReceiverZone);
                }
                payload.close();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return root;
    }

    @Override
    public void onDestroy() {
        if (mOccupantConnectionManager != null) {
            if (mConnectionState == STATE_CONNECTING) {
                mOccupantConnectionManager.cancelConnection(mReceiverZone);
            } else if (mConnectionState == STATE_CONNECTED) {
                mOccupantConnectionManager.disconnect(mReceiverZone);
            }
            mConnectionState = STATE_NOT_CONNECTED;
        }
        super.onDestroy();
    }
}
