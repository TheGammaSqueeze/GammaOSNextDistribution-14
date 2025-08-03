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

package com.android.adservices.shell;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.shell.ShellCommandServiceImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

/** Implements a service which runs the shell command in the adservices process. */
public final class AdServicesShellCommandService extends Service {
    @VisibleForTesting static final String TAG = "AdServicesShellCommand";

    /** The binder service. This field must only be accessed on the main thread. */
    @Nullable private ShellCommandServiceImpl mShellCommandService;

    @Override
    public void onCreate() {
        if (!FlagsFactory.getFlags().getAdServicesShellCommandEnabled()) {
            Log.e(TAG, "Shell command service is not enabled.");
            return;
        }

        mShellCommandService = new ShellCommandServiceImpl();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (!FlagsFactory.getFlags().getAdServicesShellCommandEnabled()) {
            Log.e(TAG, "Shell command service is not enabled.");
            return null;
        }
        return Objects.requireNonNull(mShellCommandService);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        // TODO(b/308009734): Add service and flag info to the dump.
        super.dump(fd, pw, args);
    }
}
