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

package android.telecom.cts.nonuiincallservicewoexport;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;


public class CtsNonUiInCallServiceWoExportControl extends Service {

    private static final String TAG = CtsNonUiInCallServiceWoExportControl.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.nonuiincallservicewoexport.ACTION_iCSWE_CONTROL";

    private final IBinder mCtsCompanionAppControl =
            new ICtsNonUiInCallServiceWoExportControl.Stub() {

                @Override
                public boolean hasReceivedBindRequest() {
                    Log.i(TAG, "hasReceivedBindRequest: in function");
                    return CtsNonUiInCallServiceWoExport.sBindRequestFuture.getNow(false);
                }

                @Override
                public boolean waitForBindRequest() {
                    Log.i(TAG, "waitForBindRequest: in function");
                    return CtsNonUiInCallServiceWoExport.waitForBindRequest();
                }

                @Override
                public void kill() {
                    Log.i(TAG, "kill: in function");
                    Process.killProcess(Process.myPid());
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: return control interface.");
            return mCtsCompanionAppControl;
        }
        Log.i(TAG, "onBind: invalid intent.");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: resetting");
        CtsNonUiInCallServiceWoExport.reset();
        return false;
    }

}
