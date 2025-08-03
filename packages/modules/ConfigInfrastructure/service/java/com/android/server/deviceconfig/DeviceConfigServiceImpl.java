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

package com.android.server.deviceconfig;

import android.annotation.NonNull;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.aidl.IDeviceConfigManager;
import android.provider.DeviceConfigInitializer;

import com.android.server.deviceconfig.db.DeviceConfigDbAdapter;
import com.android.server.deviceconfig.db.DeviceConfigDbHelper;

import java.io.PrintWriter;
import java.util.Map;

import com.android.modules.utils.BasicShellCommandHandler;

/**
 * DeviceConfig Service implementation (updatable via Mainline) that uses a SQLite database as a storage mechanism
 * for the configuration values.
 *
 * @hide
 */
public class DeviceConfigServiceImpl extends IDeviceConfigManager.Stub {
    private final DeviceConfigDbAdapter mDbAdapter;

    public DeviceConfigServiceImpl(Context context) {
        DeviceConfigDbHelper dbHelper = new DeviceConfigDbHelper(context);
        mDbAdapter = new DeviceConfigDbAdapter(dbHelper.getWritableDatabase());

        DeviceConfigInitializer.getDeviceConfigServiceManager()
                .getDeviceConfigUpdatableServiceRegisterer()
                .register(this);
    }

    @Override
    public Map<String, String> getProperties(String namespace, String[] names) throws RemoteException {
        return mDbAdapter.getValuesForNamespace(namespace, names);
    }

    @Override
    public boolean setProperties(String namespace, Map<String, String> values) {
        return mDbAdapter.setValues(namespace, values);
    }

    @Override
    public boolean setProperty(String namespace, String key, String value, boolean makeDefault) {
        return mDbAdapter.setValue(namespace, key, value, makeDefault);
    }

    @Override
    public  boolean deleteProperty(String namespace, String key) {
        return mDbAdapter.deleteValue(namespace, key);
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return (new MyShellCommand()).exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(),
                args);
    }

    static final class MyShellCommand extends BasicShellCommandHandler {
        // TODO(b/265948938) implement this

        @Override
        public int onCommand(String cmd) {
            if (cmd == null || "help".equals(cmd) || "-h".equals(cmd)) {
                onHelp();
                return -1;
            }
            return -1;
        }

        @Override
        public void onHelp() {
            PrintWriter pw = getOutPrintWriter();
            pw.println("Device Config implemented in mainline");
        }
    }
}
