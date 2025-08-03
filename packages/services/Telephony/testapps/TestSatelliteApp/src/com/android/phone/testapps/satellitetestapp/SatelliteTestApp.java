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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.satellite.stub.SatelliteDatagram;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * SatelliteTestApp main activity to navigate to other APIs related to satellite.
 */
public class SatelliteTestApp extends Activity {

    private static final String TAG = "SatelliteTestApp";
    public static TestSatelliteService sSatelliteService;
    private final Object mSendDatagramLock = new Object();

    private TestSatelliteServiceConnection mSatelliteServiceConn;
    private List<SatelliteDatagram> mSentSatelliteDatagrams = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mSatelliteServiceConn == null) {
            mSatelliteServiceConn = new TestSatelliteServiceConnection();
            getBaseContext().bindService(new Intent(getBaseContext(),
                    TestSatelliteService.class), mSatelliteServiceConn, Context.BIND_AUTO_CREATE);
        }

        setContentView(R.layout.activity_SatelliteTestApp);
        findViewById(R.id.SatelliteControl).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, SatelliteControl.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.Datagram).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, Datagram.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.Provisioning).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, Provisioning.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.MultipleSendReceive).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, MultipleSendReceive.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.SendReceive).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, SendReceive.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.TestSatelliteWrapper).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SatelliteTestApp.this, TestSatelliteWrapper.class);
                startActivity(intent);
            }
        });
    }

    private final ILocalSatelliteListener mSatelliteListener =
            new ILocalSatelliteListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    Log.d(TAG, "onRemoteServiceConnected");
                }

                @Override
                public void onStartSendingSatellitePointingInfo() {
                    Log.d(TAG, "onStartSendingSatellitePointingInfo");
                }

                @Override
                public void onStopSendingSatellitePointingInfo() {
                    Log.d(TAG, "onStopSendingSatellitePointingInfo");
                }

                @Override
                public void onPollPendingSatelliteDatagrams() {
                    Log.d(TAG, "onPollPendingSatelliteDatagrams");
                }

                @Override
                public void onSendSatelliteDatagram(
                        SatelliteDatagram datagram, boolean isEmergency) {
                    Log.d(TAG, "onSendSatelliteDatagram");
                    synchronized (mSendDatagramLock) {
                        mSentSatelliteDatagrams.add(datagram);
                    }
                }

                @Override
                public void onSatelliteListeningEnabled(boolean enable) {
                    Log.d(TAG, "onSatelliteListeningEnabled");
                }

                @Override
                public void onEnableCellularModemWhileSatelliteModeIsOn(boolean enable) {
                    Log.d(TAG, "onEnableCellularModemWhileSatelliteModeIsOn");
                }

                @Override
                public void onSetSatellitePlmn() {
                    Log.d(TAG, "onSetSatellitePlmn");
                }
            };

    private class TestSatelliteServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected in SatelliteTestApp");
            sSatelliteService = ((TestSatelliteService.LocalBinder) service).getService();
            sSatelliteService.setLocalSatelliteListener(mSatelliteListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected in SatelliteTestApp");
            sSatelliteService = null;
        }
    }

    public static TestSatelliteService getTestSatelliteService() {
        return sSatelliteService;
    }
}
