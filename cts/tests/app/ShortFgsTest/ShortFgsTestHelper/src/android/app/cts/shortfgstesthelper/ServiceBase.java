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
package android.app.cts.shortfgstesthelper;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.createNotification;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.HashMap;
import java.util.Map;

public abstract class ServiceBase extends Service {
    /** Class name -> instance map */
    @GuardedBy("sInstances")
    private static Map<String, ServiceBase> sInstances = new HashMap<>();

    public ServiceBase() {
        synchronized (sInstances) {
            sInstances.put(this.getClass().getName(), this);
        }
    }

    public static ServiceBase getInstanceForClass(String className) {
        synchronized (sInstances) {
            ServiceBase result = sInstances.get(className);
            if (result == null) {
                throw new RuntimeException("Service " + className + " isn't created yet");
            }
            return result;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: " + this.getClass().getName() + " startId=" + startId);

        // Handle the incoming intent.
        final ShortFgsMessage incoming = ShortFgsHelper.getMessage(intent);

        if (incoming.isDoCallStartForeground()) {
            startForeground(
                    incoming.getNotificationId(), createNotification(), incoming.getFgsType());
        }

        // If we reach here, send back the method name.
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onStartCommand", (m) -> {
            m.setServiceStartId(startId);
        });

        return incoming.getStartCommandResult();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: " + this.getClass().getName());

        // Send back the called method name.
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: " + this.getClass().getName());

        ShortFgsHelper.sendBackMethodName(this.getClass(), "onBind");

        // We don't actually use the returned object, so just return a random binder object...
        return new Binder();
    }

    @Override
    public void onTimeout(int startId) {
        Log.i(TAG, "onTimeout: " + this.getClass().getName() + " startId=" + startId);

        ShortFgsHelper.sendBackMethodName(this.getClass(), "onTimeout", (m) -> {
            m.setServiceStartId(startId);
        });
    }
}
