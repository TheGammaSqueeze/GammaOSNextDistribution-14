/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.am;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.BidiFormatter;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;

final class AppNotRespondingDialog extends BaseErrorDialog {
    private static final String TAG = "AppNotRespondingDialog";

    // Event 'what' codes
    static final int FORCE_CLOSE = 1;
    static final int WAIT = 2;
    static final int WAIT_AND_REPORT = 3;

    public static final int CANT_SHOW = -1;
    public static final int ALREADY_SHOWING = -2;

    private final ActivityManagerService mService;
    private final ProcessRecord mProc;
    private final Data mData;

    public AppNotRespondingDialog(ActivityManagerService service, Context context, Data data) {
        super(context);
        mService = service;
        mProc = data.proc;
        mData = data;
        // Disabled: no UI initialization
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Disabled: do not show any ANR UI
    }

    private final Handler mHandler = new Handler() { public void handleMessage(Message msg) { /* disabled */ } };

    @Override
    protected void closeDialog() {
        // Disabled
    }

    static class Data {
        final ProcessRecord proc;
        final ApplicationInfo aInfo;
        final boolean aboveSystem;

        // If true, then even if the user presses "WAIT" on the ANR dialog,
        // we'll show it again until the app start responding again.
        // (we only use it for input dispatch ANRs)
        final boolean isContinuousAnr;

        Data(ProcessRecord proc, ApplicationInfo aInfo, boolean aboveSystem,
                boolean isContinuousAnr) {
            this.proc = proc;
            this.aInfo = aInfo;
            this.aboveSystem = aboveSystem;
            this.isContinuousAnr = isContinuousAnr;
        }
    }
}
