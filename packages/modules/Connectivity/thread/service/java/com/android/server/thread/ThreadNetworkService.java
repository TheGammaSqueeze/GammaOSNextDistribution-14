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

package com.android.server.thread;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.thread.IThreadNetworkController;
import android.net.thread.IThreadNetworkManager;
import android.os.Binder;
import android.os.ParcelFileDescriptor;

import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the Thread network service. This is the entry point of Android Thread feature.
 */
public class ThreadNetworkService extends IThreadNetworkManager.Stub {
    private final Context mContext;
    @Nullable private ThreadNetworkCountryCode mCountryCode;
    @Nullable private ThreadNetworkControllerService mControllerService;
    private final ThreadPersistentSettings mPersistentSettings;
    @Nullable private ThreadNetworkShellCommand mShellCommand;

    /** Creates a new {@link ThreadNetworkService} object. */
    public ThreadNetworkService(Context context) {
        mContext = context;
        mPersistentSettings = ThreadPersistentSettings.newInstance(context);
    }

    /**
     * Called by {@link com.android.server.ConnectivityServiceInitializer}.
     *
     * @see com.android.server.SystemService#onBootPhase
     */
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mPersistentSettings.initialize();
            mControllerService =
                    ThreadNetworkControllerService.newInstance(mContext, mPersistentSettings);
            mControllerService.initialize();
        } else if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            // Country code initialization is delayed to the BOOT_COMPLETED phase because it will
            // call into Wi-Fi and Telephony service whose country code module is ready after
            // PHASE_ACTIVITY_MANAGER_READY and PHASE_THIRD_PARTY_APPS_CAN_START
            mCountryCode = ThreadNetworkCountryCode.newInstance(mContext, mControllerService);
            mCountryCode.initialize();
            mShellCommand = new ThreadNetworkShellCommand(mCountryCode);
        }
    }

    @Override
    public List<IThreadNetworkController> getAllThreadNetworkControllers() {
        if (mControllerService == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(mControllerService);
    }

    @Override
    public int handleShellCommand(
            @NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out,
            @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        if (mShellCommand == null) {
            return -1;
        }
        return mShellCommand.exec(
                this,
                in.getFileDescriptor(),
                out.getFileDescriptor(),
                err.getFileDescriptor(),
                args);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PERMISSION_GRANTED) {
            pw.println(
                    "Permission Denial: can't dump ThreadNetworkService from from pid="
                            + Binder.getCallingPid()
                            + ", uid="
                            + Binder.getCallingUid());
            return;
        }

        if (mCountryCode != null) {
            mCountryCode.dump(fd, pw, args);
        }

        pw.println();
    }
}
