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

package com.android.midi;

import android.content.Context;
import android.content.Intent;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.media.midi.MidiUmpDeviceService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Virtual MIDI UMP Device that copies its input to its output.
 * This is used for loop-back testing of MIDI I/O.
 *
 * Note: The application's AndroidManifest.xml should contain the following in
 * its <application> section.
 *
     <service android:name="MidiUmpEchoTestService"
         android:permission="android.permission.BIND_MIDI_DEVICE_SERVICE">
         <intent-filter>
             <action android:name="android.media.midi.MidiUmpDeviceService" />
         </intent-filter>
         <property android:name="android.media.midi.MidiUmpDeviceService"
            android:resource="@xml/ump_echo_device_info" />
     </service>

 * also it must provide an xml resource file "ump_echo_device_info.xml" containing:
     <devices>
         <device manufacturer="AndroidCTS" product="MidiUmpEcho" tags="echo,test">
             <port name="port" />
         </device>
     </devices>
 */

public class MidiUmpEchoTestService extends MidiUmpDeviceService {
    private static final String TAG = "MidiUmpEchoTestService";
    private static final boolean DEBUG = false;

    // Other apps will write to this port.
    private MidiReceiver mInputReceiver = new MyReceiver();
    // This app will copy the data to this port.
    private MidiReceiver mOutputReceiver;
    private static MidiUmpEchoTestService sInstance;

    // These are public so we can easily read them from CTS test.
    public int statusChangeCount;
    public boolean inputOpened;
    public int outputOpenCount;
    public int serviceCloseCount;

    private final Object mCloseLock = new Object();

    public static final String TEST_MANUFACTURER = "AndroidCTS";
    public static final String ECHO_PRODUCT = "MidiUmpEcho";

    /**
     * Search through the available devices for the UMP ECHO loop-back device.
     */
    public static MidiDeviceInfo findEchoDevice(Context context) {
        MidiManager midiManager = context.getSystemService(MidiManager.class);
        Collection<MidiDeviceInfo> infos = midiManager.getDevicesForTransport(
                MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS);
        MidiDeviceInfo echoInfo = null;
        for (MidiDeviceInfo info : infos) {
            Bundle properties = info.getProperties();
            String manufacturer = properties.getString(
                    MidiDeviceInfo.PROPERTY_MANUFACTURER);

            if (TEST_MANUFACTURER.equals(manufacturer)) {
                String product = properties.getString(
                        MidiDeviceInfo.PROPERTY_PRODUCT);
                if (ECHO_PRODUCT.equals(product)) {
                    echoInfo = info;
                    break;
                }
            }
        }
        if (DEBUG) {
            Log.i(TAG, "MidiEchoService for " + ECHO_PRODUCT + ": " + echoInfo);
        }
        return echoInfo;
    }

    /**
     * @return A textual name for this echo service.
     */
    public static String getEchoServerName() {
        return ECHO_PRODUCT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.i(TAG, "#### onCreate()");
        }
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) {
            Log.i(TAG, "#### onDestroy()");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.i(TAG, "#### onStartCommand()");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) {
            Log.i(TAG, "#### onBind()");
        }
        return super.onBind(intent);
    }

    // Allows tests to read fields.
    public static MidiUmpEchoTestService getInstance() {
        return sInstance;
    }

    @Override
    public List<MidiReceiver> onGetInputPortReceivers() {
        return new ArrayList<MidiReceiver>(Collections.singletonList(mInputReceiver));
    }

    class MyReceiver extends MidiReceiver {
        @Override
        public void onSend(byte[] data, int offset, int count, long timestamp)
                throws IOException {
            if (mOutputReceiver == null) {
                mOutputReceiver = getOutputPortReceivers().get(0);
            }
            // Copy input to output.
            mOutputReceiver.send(data, offset, count, timestamp);
        }
    }

    /**
     * Waits deadlineMillis or until onClose is called.
     */
    public void waitForClose(int deadlineMillis) {
        try {
            long now = System.currentTimeMillis();
            long deadline = now + deadlineMillis;
            synchronized (mCloseLock) {
                int initialServiceCloseCount = serviceCloseCount;
                boolean condition = initialServiceCloseCount != serviceCloseCount;
                while (!condition && now < deadline) {
                    mCloseLock.wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "waitForClose interrupted");
        }
    }

    @Override
    public void onDeviceStatusChanged(MidiDeviceStatus status) {
        statusChangeCount++;
        if (status != null) {
            inputOpened = status.isInputPortOpen(0);
            outputOpenCount = status.getOutputPortOpenCount(0);
        } else {
            inputOpened = false;
            outputOpenCount = 0;
        }
    }

    @Override
    public void onClose() {
        synchronized (mCloseLock) {
            serviceCloseCount++;
            mCloseLock.notifyAll();
        }
    }
}
