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

package android.app.cts;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * Base class supporting "chained" instrumentation: start another instrumentation while
 * running the current instrumentation.
 */
public class BaseChainedInstrumentation extends Instrumentation {
    static final String EXTRA_MESSENGER = "messenger";

    final ComponentName mNestedInstrComp;

    /** Constructor */
    public BaseChainedInstrumentation(ComponentName nestedInstrComp) {
        mNestedInstrComp = nestedInstrComp;
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        final String proc = getProcessName();
        final String appProc = Application.getProcessName();
        if (!proc.equals(appProc)) {
            throw new RuntimeException(String.format(
                "getProcessName()s mismatch. Instr=%s App=%s", proc, appProc));
        }
        final Bundle result = new Bundle();
        result.putBoolean(proc, true);
        if (mNestedInstrComp != null) {
            // We're in the main process.
            // Because the Context#startInstrumentation doesn't support result watcher,
            // we'd have to craft a private way to relay the result back.
            final Handler handler = new Handler(Looper.myLooper(), msg -> {
                final Bundle nestedResult = (Bundle) msg.obj;
                result.putAll(nestedResult);
                finish(Activity.RESULT_OK, result);
                return true;
            });
            final Messenger messenger = new Messenger(handler);
            final Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_MESSENGER, messenger);
            getContext().startInstrumentation(mNestedInstrComp, null, extras);
            scheduleTimeoutCleanup();
        } else {
            final Messenger messenger = arguments.getParcelable(EXTRA_MESSENGER, Messenger.class);
            final Message msg = Message.obtain();
            try {
                msg.obj = result;
                messenger.send(msg);
            } catch (RemoteException e) {
            } finally {
                msg.recycle();
            }
            finish(Activity.RESULT_OK, result);
        }
    }

    private void scheduleTimeoutCleanup() {
        new Handler(Looper.myLooper()).postDelayed(() -> {
            Bundle result = new Bundle();
            result.putString("FAILURE",
                    "Timed out waiting for sub-instrumentation to complete");
            finish(Activity.RESULT_CANCELED, result);
        }, 20 * 1000);
    }
}
