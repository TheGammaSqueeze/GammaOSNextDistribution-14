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

package com.android.musicfx;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * This Service provides a way for client to bind to MusicFx, so that MusicFx can run at same
 * Process State (see @ProcessStateEnum) as the client.
 *
 * Currently, MusicFx does not run in a high priority state (often in CACHED_EMPTY) and can be
 * easily killed by the LowMemoryKiller. However, its users (music apps, for example) are usually
 * in a higher priority state, which means they are less likely to be killed. This can lead to
 * MusicFx and its users being out of sync. To avoid this, framework side will keep record of all
 * active MusicFx audio sessions, promote the procstate of MusicFx to foreground with the first
 * audio session open, and remove the foreground procstate delegate with the last audio session
 * close, or the last user of MusicFx is gone.
 *
 * MusicFx user APPs do not need to do anything.
 *
 */
public class KeepAliveService extends Service {
    private final String TAG = "MusicFxKeepAliveService";

    // Binder given to clients with onBind() callback, the client app receive it as IBinder
    // parameter of onServiceConnected which can not be used.
    private final IBinder mBinder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind with intent " + intent);
        return mBinder;
    }
}
