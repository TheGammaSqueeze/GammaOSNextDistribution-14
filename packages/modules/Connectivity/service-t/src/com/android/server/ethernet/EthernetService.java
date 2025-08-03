/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.ethernet;

import android.content.Context;
import android.net.INetd;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.util.Objects;

// TODO: consider renaming EthernetServiceImpl to EthernetService and deleting this file.
public final class EthernetService {
    private static final String TAG = "EthernetService";
    private static final String THREAD_NAME = "EthernetServiceThread";

    private static INetd getNetd(Context context) {
        final INetd netd =
                INetd.Stub.asInterface((IBinder) context.getSystemService(Context.NETD_SERVICE));
        Objects.requireNonNull(netd, "could not get netd instance");
        return netd;
    }

    public static EthernetServiceImpl create(Context context) {
        final HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        final EthernetNetworkFactory factory = new EthernetNetworkFactory(handler, context);
        return new EthernetServiceImpl(context, handler,
                new EthernetTracker(context, handler, factory, getNetd(context)));
    }
}
