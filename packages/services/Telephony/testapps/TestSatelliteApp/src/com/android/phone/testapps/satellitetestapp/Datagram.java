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

import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.telephony.satellite.stub.SatelliteResult;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Activity related to Datagram APIs.
 */
public class Datagram extends Activity {

    private static final String TAG = "Datagram";
    private static final int MAX_NUMBER_OF_STORED_STATES = 3;
    private int mTransferState;
    private int mModemState;
    LinkedList<Integer> mModemStateQueue = new LinkedList<>();
    LinkedList<Integer> mSendQueue = new LinkedList<>();
    LinkedList<Integer> mReceiveQueue = new LinkedList<>();

    private SatelliteManager mSatelliteManager;
    private SatelliteDatagramCallbackTestApp mDatagramCallback;
    private SatelliteModemStateCallbackTestApp mStateCallback;
    private SatelliteTransmissionUpdateCallbackTestApp mCallback;
    private android.telephony.satellite.stub.SatelliteDatagram mReceivedDatagram;

    private String mShowSatelliteModemStateTransition;
    private String mShowDatagramSendStateTransition;
    private String mShowDatagramReceiveStateTransition;
    private static final long TIMEOUT = 3000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSatelliteManager = getSystemService(SatelliteManager.class);
        mDatagramCallback = new SatelliteDatagramCallbackTestApp();
        mStateCallback = new SatelliteModemStateCallbackTestApp();
        mCallback = new SatelliteTransmissionUpdateCallbackTestApp();

        mReceivedDatagram = new android.telephony.satellite.stub.SatelliteDatagram();

        setContentView(R.layout.activity_Datagram);
        findViewById(R.id.startSatelliteTransmissionUpdates)
                .setOnClickListener(this::startTransmissionUpdatesApp);
        findViewById(R.id.stopSatelliteTransmissionUpdates)
                .setOnClickListener(this::stopTransmissionUpdatesApp);
        findViewById(R.id.pollPendingSatelliteDatagrams)
                .setOnClickListener(this::pollPendingDatagramsApp);
        findViewById(R.id.sendSatelliteDatagram)
                .setOnClickListener(this::sendDatagramApp);
        findViewById(R.id.registerForSatelliteDatagram)
                .setOnClickListener(this::registerForIncomingDatagramApp);
        findViewById(R.id.unregisterForSatelliteDatagram)
                .setOnClickListener(this::unregisterForIncomingDatagramApp);
        findViewById(R.id.showDatagramSendStateTransition)
                .setOnClickListener(this::showDatagramSendStateTransitionApp);
        findViewById(R.id.showDatagramReceiveStateTransition)
                .setOnClickListener(this::showDatagramReceiveStateTransitionApp);
        findViewById(R.id.registerForSatelliteModemStateChanged)
                .setOnClickListener(this::registerForModemStateChangedApp);
        findViewById(R.id.unregisterForSatelliteModemStateChanged)
                .setOnClickListener(this::unregisterForModemStateChangedApp);
        findViewById(R.id.showSatelliteModemStateTransition)
                .setOnClickListener(this::showSatelliteModemStateTransitionApp);

        findViewById(R.id.Back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Datagram.this, SatelliteTestApp.class));
            }
        });
    }

    protected class SatelliteDatagramCallbackTestApp implements SatelliteDatagramCallback {
        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, Consumer<Void> callback) {
            Log.d(TAG, "onSatelliteDatagramReceived in TestApp: datagramId =" + datagramId
                    + ", datagram =" + datagram + ", pendingCount=" + pendingCount);
        }
    }

    protected class SatelliteModemStateCallbackTestApp implements SatelliteModemStateCallback {
        @Override
        public void onSatelliteModemStateChanged(int state) {
            mModemState = state;
            mModemStateQueue.addLast(state);
            if (mModemStateQueue.size() > MAX_NUMBER_OF_STORED_STATES) {
                mModemStateQueue.removeFirst();
            }
            mShowSatelliteModemStateTransition = getSatelliteModemStateTransition(mModemStateQueue);
            Log.d(TAG, "onSatelliteModemStateChanged in TestApp: state=" + mModemState);
        }
    }
    protected class SatelliteTransmissionUpdateCallbackTestApp implements
            SatelliteTransmissionUpdateCallback {
        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            Log.d(TAG, "onSatellitePositionChanged in TestApp: pointingInfo =" + pointingInfo);
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            mTransferState = state;
            mSendQueue.addLast(state);
            if (mSendQueue.size() > MAX_NUMBER_OF_STORED_STATES) {
                mSendQueue.removeFirst();
            }
            mShowDatagramSendStateTransition = getTransferStateTransition(mSendQueue);
            Log.d(TAG, "onSendDatagramStateChanged in TestApp: state =" + mTransferState
                    + ", sendPendingCount =" + sendPendingCount + ", errorCode=" + errorCode);
        }

        @Override
        public void onReceiveDatagramStateChanged(
                int state, int receivePendingCount, int errorCode) {
            mTransferState = state;
            mReceiveQueue.addLast(state);
            if (mReceiveQueue.size() > MAX_NUMBER_OF_STORED_STATES) {
                mReceiveQueue.removeFirst();
            }
            mShowDatagramReceiveStateTransition = getTransferStateTransition(mReceiveQueue);
            Log.d(TAG, "onReceiveDatagramStateChanged in TestApp: state=" + mTransferState
                    + ", receivePendingCount=" + receivePendingCount + ", errorCode=" + errorCode);
        }
    }

    private void startTransmissionUpdatesApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, error::offer);
        TextView showErrorStatusTextView = findViewById(R.id.showErrorStatus);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                showErrorStatusTextView.setText("Timed out to enable the satellite");
                return;
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
        mSatelliteManager.startTransmissionUpdates(Runnable::run, error::offer, mCallback);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to startSatelliteTransmissionUpdates");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to startSatelliteTransmissionUpdates with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("startSatelliteTransmissionUpdates is successful");
            }
        } catch (InterruptedException e) {
            textView.setText("startSatelliteTransmissionUpdates exception caught =" + e);
        }
    }

    private void stopTransmissionUpdatesApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        mSatelliteManager.stopTransmissionUpdates(mCallback, Runnable::run, error::offer);
        try {
            Integer value = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out to stopSatelliteTransmissionUpdates");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to stopSatelliteTransmissionUpdates with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("stopSatelliteTransmissionUpdates is successful");
            }
        } catch (InterruptedException e) {
            textView.setText("stopSatelliteTransmissionUpdates exception caught =" + e);
        }
    }
    private void pollPendingDatagramsApp(View view) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        TextView showErrorStatusTextView = findViewById(R.id.showErrorStatus);
        TextView textView = findViewById(R.id.text_id);
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
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
                return;
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                showErrorStatusTextView.setText("Failed to enable satellite, error = "
                        + SatelliteErrorUtils.mapError(value));
                return;
            }
            resultListener.clear();
            Log.d(TAG, "Poll to check queue is cleared = "
                    + resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            showErrorStatusTextView.setText("Enable SatelliteService exception caught = " + e);
            return;
        }
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out for poll message");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to pollPendingDatagrams with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("pollPendingDatagrams is successful");
            }
        } catch (InterruptedException e) {
            textView.setText("pollPendingDatagrams exception caught =" + e);
        }
    }

    private void sendDatagramApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            if (value == null) {
                textView.setText("Timed out for sendDatagram");
            } else if (value != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                textView.setText("Failed to sendDatagram with error = "
                        + SatelliteErrorUtils.mapError(value));
            } else {
                textView.setText("sendDatagram is successful");
            }
        } catch (InterruptedException e) {
            textView.setText("sendDatagram exception caught =" + e);
        }
    }

    private void registerForIncomingDatagramApp(View view) {
        int result = mSatelliteManager.registerForIncomingDatagram(Runnable::run,
                mDatagramCallback);
        TextView textView = findViewById(R.id.text_id);
        if (result == 0) {
            textView.setText("registerForIncomingDatagram is successful");
        } else {
            textView.setText("Status for registerForIncomingDatagram : "
                    + SatelliteErrorUtils.mapError(result));
        }
    }

    private void unregisterForIncomingDatagramApp(View view) {
        mSatelliteManager.unregisterForIncomingDatagram(mDatagramCallback);
        TextView textView = findViewById(R.id.text_id);
        textView.setText("unregisterForIncomingDatagram is successful");
    }

    private void showDatagramSendStateTransitionApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("Last datagram send state transition is : "
                + mShowDatagramSendStateTransition);
    }

    private void showDatagramReceiveStateTransitionApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText("Last datagram receive state transition is : "
                + mShowDatagramReceiveStateTransition);
    }

    private void registerForModemStateChangedApp(View view) {
        int result = mSatelliteManager.registerForModemStateChanged(Runnable::run,
                mStateCallback);
        TextView textView = findViewById(R.id.text_id);
        if (result == 0) {
            textView.setText("registerForModemStateChanged is successful");
        } else {
            textView.setText("Status for registerForModemStateChanged : "
                    + SatelliteErrorUtils.mapError(result));
        }
    }

    private void unregisterForModemStateChangedApp(View view) {
        mSatelliteManager.unregisterForModemStateChanged(mStateCallback);
        TextView textView = findViewById(R.id.text_id);
        textView.setText("unregisterForModemStateChanged is successful");
    }

    private void showSatelliteModemStateTransitionApp(View view) {
        TextView textView = findViewById(R.id.text_id);
        textView.setText(
                    "Last modem transition state is: " + mShowSatelliteModemStateTransition);
    }

    private String getSatelliteModemStateName(@SatelliteManager.SatelliteModemState int state) {
        switch (state) {
            case SatelliteManager.SATELLITE_MODEM_STATE_IDLE:
                return "IDLE";
            case SatelliteManager.SATELLITE_MODEM_STATE_LISTENING:
                return "LISTENING";
            case SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING:
                return "DATAGRAM_TRANSFERRING";
            case SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_RETRYING:
                return "DATAGRAM_RETRYING";
            case SatelliteManager.SATELLITE_MODEM_STATE_OFF:
                return "OFF";
            case SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE:
                return "UNAVAILABLE";
            default: return "UNKNOWN";
        }
    }

    private String getSatelliteModemStateTransition(LinkedList<Integer> states) {
        StringBuilder sb = new StringBuilder();
        for (int state : states) {
            sb.append(getSatelliteModemStateName(state));
            sb.append("=>");
        }
        if (!sb.isEmpty()) {
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

    private String getDatagramTransferStateName(
            @SatelliteManager.SatelliteDatagramTransferState int state) {
        switch (state) {
            case SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE: return "IDLE";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING: return "SENDING";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS: return "SEND_SUCCESS";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED: return "SEND_FAILED";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING: return "RECEIVING";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS: return "RECEIVE_SUCCESS";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE: return "RECEIVE_NONE";
            case SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED: return "RECEIVE_FAILED";
            default: return "UNKNOWN";
        }
    }

    private String getTransferStateTransition(LinkedList<Integer> states) {
        StringBuilder sb = new StringBuilder();
        for (int state : states) {
            sb.append(getDatagramTransferStateName(state));
            sb.append("=>");
        }
        if (!sb.isEmpty()) {
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sh = getSharedPreferences("TestSatelliteSharedPref", MODE_PRIVATE);
        String modemStateTransition = sh.getString("modem_state",
                mShowSatelliteModemStateTransition);
        String datagramSendStateTransition = sh.getString("datagram_send_state",
                mShowDatagramSendStateTransition);
        String datagramReceiveStateTransition = sh.getString("datagram_receive_state",
                mShowDatagramReceiveStateTransition);

        // Setting the fetched data
        mShowSatelliteModemStateTransition = modemStateTransition;
        mShowDatagramSendStateTransition = datagramSendStateTransition;
        mShowDatagramReceiveStateTransition = datagramReceiveStateTransition;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences = getSharedPreferences("TestSatelliteSharedPref",
                    MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("modem_state", mShowSatelliteModemStateTransition);
        myEdit.putString("datagram_send_state", mShowDatagramSendStateTransition);
        myEdit.putString("datagram_receive_state", mShowDatagramReceiveStateTransition);
        myEdit.apply();
    }

    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences sharedPreferences = getSharedPreferences("TestSatelliteSharedPref",
                    MODE_PRIVATE);
        final SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();

        sharedPrefsEditor.remove("modem_state");
        sharedPrefsEditor.remove("datagram_send_state");
        sharedPrefsEditor.remove("datagram_receive_state");
        sharedPrefsEditor.commit();
    }
}
