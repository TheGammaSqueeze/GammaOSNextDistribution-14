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

package android.hardware.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.HardwareBuffer;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import cts.android.hardware.IHardwareBufferTestService;

public class HardwareBufferTestService extends Service {

    static {
        System.loadLibrary("ctshardware_jni");
    }

    private IBinder mBinder;

    HardwareBufferTestService(IBinder binder) {
        mBinder = binder;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static class JavaLocal extends HardwareBufferTestService {
        public JavaLocal() {
            super(makeJavaService());
        }
    }
    public static class JavaRemote extends HardwareBufferTestService {
        public JavaRemote() {
            super(makeJavaService());
        }
    }
    public static class NativeLocal extends HardwareBufferTestService {
        public NativeLocal() {
            super(makeNativeService());
        }
    }
    public static class NativeRemote extends HardwareBufferTestService {
        public NativeRemote() {
            super(makeNativeService());
        }
    }

    static native IBinder makeNativeService();
    static IBinder makeJavaService() {
        return new IHardwareBufferTestService.Stub() {
            @Override
            public long getId(HardwareBuffer buffer) throws RemoteException {
                return buffer.getId();
            }

            @Override
            public HardwareBuffer createBuffer(int width, int height) throws RemoteException {
                return HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1,
                        HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_CPU_WRITE_OFTEN);
            }
        };
    }

    public static IHardwareBufferTestService connect(Context context, Class serviceClass) {
        return new SyncTestServiceConnection(context, serviceClass).get();
    }

    private static class SyncTestServiceConnection implements ServiceConnection {
        private static final String TAG = "SyncServiceConnection";

        private Class mServiceProviderClass;
        private Context mContext;

        private IHardwareBufferTestService mInterface;
        private boolean mInvalid = false;  // if the service has disconnected abrubtly

        SyncTestServiceConnection(Context context, Class serviceClass) {
            mContext = context;
            mServiceProviderClass = serviceClass;
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized (this) {
                mInterface = IHardwareBufferTestService.Stub.asInterface(service);
                Log.d(TAG, "Service has connected: " + mServiceProviderClass);
                this.notify();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Service has disconnected: " + mServiceProviderClass);
            synchronized (this) {
                mInterface = null;
                mInvalid = true;
                this.notify();
            }
        }

        IHardwareBufferTestService get() {
            synchronized (this) {
                if (!mInvalid && mInterface == null) {
                    Intent intent = new Intent(mContext, mServiceProviderClass);
                    intent.setAction(IHardwareBufferTestService.class.getName());
                    mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);

                    try {
                        this.wait(5000 /* ms */);
                    } catch (InterruptedException e) { }
                }
            }
            return mInterface;
        }
    }
}
