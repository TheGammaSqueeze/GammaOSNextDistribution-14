/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voiceinteraction.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.time.Duration;

/**
 * This service is used to bypass the requirement for BIND_VOICE_INTERACTION permission.
 * The permission is not required for services running in the same process so instead this proxy
 * service is used to forward the interface outside this process without needed the permission
 * granted.
 *
 * While not ideal, it is a workaround until the instrumented test classes are in the same APK
 * (or have the same UID) as the test service implementations.
 */
// TODO: remove the need for this service by having the same UID for test classes and services
public class VoiceInteractionServiceBindingHelper extends Service {
    private static final String TAG = VoiceInteractionServiceBindingHelper.class.getSimpleName();
    private static final Duration WAIT_TO_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final Object mLock = new Object();
    private ITestVoiceInteractionService mTestService;
    private final ConditionVariable mTestServiceConnected = new ConditionVariable();
    private final ServiceConnection mTestServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            synchronized (mLock) {
                mTestService = (ITestVoiceInteractionService) service;
            }
            mTestServiceConnected.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            mTestServiceConnected.close();
            synchronized (mLock) {
                mTestService = null;
            }
        }
    };
    private final IVoiceInteractionServiceBindingHelper mClientInterface =
            new IVoiceInteractionServiceBindingHelper.Stub() {

                private void enforceTestServiceIsConnected() throws RemoteException {
                    Log.d(TAG, "waiting for service to connect");
                    if (!mTestServiceConnected.block(WAIT_TO_CONNECT_TIMEOUT.toMillis())) {
                        throw new RemoteException(
                                "not connected to "
                                        + Utils.PROXY_VOICE_INTERACTION_SERVICE_CLASS_NAME);
                    }
                }

                @Override
                public ITestVoiceInteractionService getVoiceInteractionService()
                        throws RemoteException {
                    enforceTestServiceIsConnected();
                    synchronized (mLock) {
                        return mTestService;
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(Utils.ACTION_BIND_TEST_VOICE_INTERACTION);
        serviceIntent.setComponent(
                new ComponentName(Utils.TEST_VOICE_INTERACTION_SERVICE_PACKAGE_NAME,
                        Utils.PROXY_VOICE_INTERACTION_SERVICE_CLASS_NAME));
        Log.d(TAG, "binding to test service");
        VoiceInteractionServiceBindingHelper.this.bindService(serviceIntent, mTestServiceConnection,
                BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mTestServiceConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Utils.ACTION_BIND_TEST_VOICE_INTERACTION.equals(intent.getAction())) {
            return mClientInterface.asBinder();
        }
        Log.e(TAG, "incorrect action provided: " + intent);
        return null;
    }
}
