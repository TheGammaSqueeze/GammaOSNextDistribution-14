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
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Activity related to Send and Receiving of message APIs for satellite.
 */
public class MultipleSendReceive extends Activity {

    private static final String TAG = "MultipleSendReceive";

    private SatelliteManager mSatelliteManager;
    private android.telephony.satellite.stub.SatelliteDatagram mReceivedDatagram;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSatelliteManager = getSystemService(SatelliteManager.class);
        mReceivedDatagram = new android.telephony.satellite.stub.SatelliteDatagram();

        setContentView(R.layout.activity_MultipleSendReceive);
        findViewById(R.id.multiplePollPendingSatelliteDatagrams)
                .setOnClickListener(this::multiplePollPendingSatelliteDatagramsApp);
        findViewById(R.id.multipleSendSatelliteDatagram)
                .setOnClickListener(this::multipleSendSatelliteDatagramApp);
        findViewById(R.id.multipleSendReceiveSatelliteDatagram)
                .setOnClickListener(this::multipleSendReceiveSatelliteDatagramApp);
        findViewById(R.id.Back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MultipleSendReceive.this, SatelliteTestApp.class));
            }
        });
    }

    private void multiplePollPendingSatelliteDatagramsApp(View view) {
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
        SatelliteTestApp.getTestSatelliteService().sendOnPendingDatagrams();
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 4);
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, resultListener::offer);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 3);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 2);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 1);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 0);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(1000, TimeUnit.MILLISECONDS);
            TextView textView = findViewById(R.id.text_id);
            if (value == 0) {
                textView.setText("multiplePollPendingSatelliteDatagrams is Successful");
            } else {
                textView.setText("Status for multiplePollPendingSatelliteDatagrams : "
                        + SatelliteErrorUtils.mapError(value));
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "exception caught =" + e);
        }
    }


    private void multipleSendSatelliteDatagramApp(View view) {
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        try {
            Integer value = resultListener.poll(1000, TimeUnit.MILLISECONDS);
            TextView textView = findViewById(R.id.text_id);
            if (value == 0) {
                textView.setText("multipleSendSatelliteDatagram is Successful");
            } else {
                textView.setText("Status for multipleSendSatelliteDatagram : "
                        + SatelliteErrorUtils.mapError(value));
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "exception caught =" + e);
        }
    }

    private void multipleSendReceiveSatelliteDatagramApp(View view) {
        mSatelliteManager.setDeviceAlignedWithSatellite(true);
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        mSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(true).setDemoMode(true).build(),
                Runnable::run, resultListener::offer);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 4);
        mSatelliteManager.pollPendingDatagrams(Runnable::run, resultListener::offer);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 3);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 2);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 1);
        mSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, Runnable::run, resultListener::offer);
        SatelliteTestApp.getTestSatelliteService().sendOnSatelliteDatagramReceived(
                mReceivedDatagram, 0);
        try {
            Integer value = resultListener.poll(1000, TimeUnit.MILLISECONDS);
            TextView textView = findViewById(R.id.text_id);
            if (value == 0) {
                textView.setText("multipleSendReceiveSatelliteDatagram is Successful");
            } else {
                textView.setText("Status for multipleSendReceiveSatelliteDatagram : "
                        + SatelliteErrorUtils.mapError(value));
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "exception caught =" + e);
        }
    }
}
