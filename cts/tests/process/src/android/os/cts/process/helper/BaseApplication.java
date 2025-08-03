/*
 * Copyright 2021 The Android Open Source Project
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
package android.os.cts.process.helper;

import android.app.Application;
import android.os.cts.process.common.Consts;
import android.os.cts.process.common.Message;
import android.util.Log;

import com.android.compatibility.common.util.BroadcastMessenger;

public abstract class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(Consts.TAG, "onCreate: this=" + this);

        sendBackApplicationCreated();
    }

    private void sendBackApplicationCreated() {
        Message m = new Message();

        m.fillInBasicInfo(this);

        m.applicationClassName = this.getClass().getCanonicalName();

        BroadcastMessenger.send(this, Consts.TAG, m);
    }
}
