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

package com.android.phone.testapps.satellitetestapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.telephony.satellite.stub.SatelliteResult;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Activity related to Send and Receiving of message APIs for satellite.
 */
public class SendReceive extends Activity {

    private static final String TAG = "SendReceive";

    private SatelliteManager mSatelliteManager;
    private SendReceive.SatelliteDatagramCallbackTestApp mCallback;

    private PointingInfo mPointingInfo;
    private String mMessageInput = "";
    private String mMessageOutput = "";
    private static final long TIMEOUT = 3000;

    private EditText mEnterMessage;
    private TextView mMessageStatusTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSatelliteManager = getSystemService(SatelliteManager.class);
        mCallback = new SendReceive.SatelliteDatagramCallbackTestApp();

        setContentView(R.layout.activity_SendReceive);
        findViewById(R.id.sendMessage).setOnClickListener(this::sendStatusApp);
        findViewById(R.id.receiveMessage).setOnClickListener(this::receiveStatusApp);
        mEnterMessage = (EditText) findViewById(R.id.enterText);
        mMessageStatusTextView = findViewById(R.id.messageStatus);
        findViewById(R.id.Back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SendReceive.this, SatelliteTestApp.class));
            }
        });
    }

    protected class SatelliteDatagramCallbackTestApp implements SatelliteDatagramCallback {
        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, Consumer<Void> callback) {
            Log.d(TAG, "onSatelliteDatagramReceived in TestApp: datagramId =" + datagramId
                    + ", datagram =" + datagram + ", pendingCount=" + pendingCount);
            mMessageStatusTextView.setText("Last received satellite message is = "
                    + new String(datagram.getSatelliteDatagram()));
        }
    }

    protected class SatelliteTransmissionUpdateCallbackTestApp implements
            SatelliteTransmissionUpdateCallback {
        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            mPointingInfo = pointingInfo;
            Log.d(TAG, "onSatellitePositionChanged in TestApp for sendReceive: pointingInfo = "
                    + mPointingInfo);
            TextView satellitePositionTextView = findViewById(R.id.satellitePosition);
            satellitePositionTextView.setText("Successfully received the satellite position : "
                    + mPointingInfo);
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            Log.d(TAG, "onSendDatagramStateChanged in TestApp for sendReceive: state = "
                    + state + ", sendPendingCount =" + sendPendingCount + ", errorCode="
                    + errorCode);
        }

        @Override
        public void onReceiveDatagramStateChanged(
                int state, int receivePendingCount, int errorCode) {
            Log.d(TAG, "onReceiveDatagramStateChanged in TestApp for sendReceive: state = "
                    + state + ", " + "receivePendingCount=" + receivePendingCount + ", errorCode="
                    + errorCode);
        }
    }

    private void sendStatusApp(View view) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mMessageInput = mEnterMessage.getText().toString();
        mMessageOutput = mEnterMessage.getText().toString();
        byte[] testProvisionData = mMessageInput.getBytes();
        setupForTransferringDatagram(testProvisionData);

        SatelliteDatagram datagram = new SatelliteDatagram(mMessageInput.getBytes());
        //Sending Message
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, error::offer);
        TextView messageStatusTextView = findViewById(R.id.messageStatus);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                messageStatusTextView.setText("Timed out to send the message");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                messageStatusTextView.setText("Failed to send the message, error ="
                        + SatelliteErrorUtils.mapError(value));
            } else {
                messageStatusTextView.setText("Successfully sent the message = "
                        + mEnterMessage.getText().toString());
            }
        } catch (InterruptedException e) {
            messageStatusTextView.setText("sendDatagram exception caught = " + e);
        }
    }

    private void receiveStatusApp(View view) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        byte[] testProvisionData = mMessageOutput.getBytes();
        setupForTransferringDatagram(testProvisionData);

        int result = mSatelliteManager.registerForIncomingDatagram(Runnable::run, mCallback);
        TextView showErrorStatusTextView = findViewById(R.id.showErrorStatus);
        if (result != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            showErrorStatusTextView.setText("Status for registerForIncomingDatagram : "
                    + SatelliteErrorUtils.mapError(result));
        }
        if (SatelliteTestApp.getTestSatelliteService() != null) {
            SatelliteTestApp.getTestSatelliteService().sendOnPendingDatagrams();
        }
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                showErrorStatusTextView.setText("Timed out to enable the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                showErrorStatusTextView.setText("Failed to enable satellite, error = "
                        + SatelliteErrorUtils.mapError(value));
                return;
            }
            resultListener.clear();
        } catch (InterruptedException e) {
            showErrorStatusTextView.setText("Enable SatelliteService exception caught = " + e);
            return;
        }

        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                mMessageStatusTextView.setText("Timed out to poll pending messages");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                mMessageStatusTextView.setText("Failed to poll pending messages, error = "
                        + SatelliteErrorUtils.mapError(value));
            }  else {
                mMessageStatusTextView.setText("Successfully polled pending messages");
            }
        } catch (InterruptedException e) {
            mMessageStatusTextView.setText("pollPendingDatagrams exception caught = " + e);
        }
    }

    private void setupForTransferringDatagram(byte[] provisionData) {
        TextView showErrorStatusTextView = findViewById(R.id.showErrorStatus);
        CancellationSignal cancellationSignal = new CancellationSignal();
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        //Provisioning
        mSatelliteManager.provisionService("SATELLITE_TOKEN", provisionData,
                cancellationSignal, Runnable::run, error::offer);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                showErrorStatusTextView.setText("Timed out to provision the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                showErrorStatusTextView.setText("Failed to provision satellite, error = "
                        + SatelliteErrorUtils.mapError(value));
                return;
            }
        } catch (InterruptedException e) {
            showErrorStatusTextView.setText("Provision SatelliteService exception caught = " + e);
            return;
        }
        error.clear();

        //Static position of device
        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        capabilities.set(result);
                        TextView devicePositionTextView = findViewById(R.id.devicePosition);
                        devicePositionTextView.setText("Successfully receive the device position : "
                                + capabilities.get().getAntennaPositionMap());
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        TextView devicePositionTextView = findViewById(R.id.devicePosition);
                        devicePositionTextView.setText("Unable to fetch device position error is : "
                                + SatelliteErrorUtils.mapError(errorCode.get()));
                    }
                };
        mSatelliteManager.requestCapabilities(Runnable::run, receiver);

        //Satellite Position
        SatelliteTransmissionUpdateCallbackTestApp callback =
                new SatelliteTransmissionUpdateCallbackTestApp();
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, error::offer);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                showErrorStatusTextView.setText("Timed out to enable the satellite");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                showErrorStatusTextView.setText("Failed to enable satellite, error = "
                        + SatelliteErrorUtils.mapError(value));
                return;
            }
        } catch (InterruptedException e) {
            showErrorStatusTextView.setText("Enable SatelliteService exception caught = " + e);
            return;
        }
        error.clear();

        mSatelliteManager.startTransmissionUpdates(Runnable::run, error::offer, callback);
        // Position update
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 50.5f;
        pointingInfo.satelliteElevation = 20.36f;
        if (SatelliteTestApp.getTestSatelliteService() != null) {
            SatelliteTestApp.getTestSatelliteService().sendOnSatellitePositionChanged(pointingInfo);
        }
        TextView satellitePositionTextView = findViewById(R.id.satellitePosition);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                satellitePositionTextView.setText("Failed to register for satellite transmission"
                        + "updates");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                satellitePositionTextView.setText("Failed to register for satellite transmission "
                        + "updates, error = " + SatelliteErrorUtils.mapError(value));
            }
        } catch (InterruptedException e) {
            satellitePositionTextView.setText("startSatelliteTransmissionUpdates exception caught ="
                        + e);
        }
        //Device is aligned with the satellite for demo mode
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
    }
}
