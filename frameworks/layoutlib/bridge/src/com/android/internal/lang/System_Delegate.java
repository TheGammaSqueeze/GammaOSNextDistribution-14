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
package com.android.internal.lang;

import com.android.layoutlib.bridge.android.BridgeContext;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import static com.android.layoutlib.bridge.impl.RenderAction.getCurrentContext;

/**
 * Provides alternative implementations of methods that don't exist on the host VM.
 * This also providers a time control that allows to set a specific system time.
 *
 * @see ReplaceMethodCallsAdapter
 */
@SuppressWarnings("unused")
public class System_Delegate {
    public static void log(String message) {
        // ignore.
    }

    public static void log(String message, Throwable th) {
        // ignore.
    }

    public static void setNanosTime(long nanos) {
        BridgeContext context = getCurrentContext();
        if (context != null) {
            context.getSessionInteractiveData().setNanosTime(nanos);
        }
    }

    public static void setBootTimeNanos(long nanos) {
        BridgeContext context = getCurrentContext();
        if (context != null) {
            context.getSessionInteractiveData().setBootNanosTime(nanos);
        }
    }

    public static long nanoTime() {
        BridgeContext context = getCurrentContext();
        if (context != null) {
            return context.getSessionInteractiveData().getNanosTime();
        }
        return 0;
    }

    public static long currentTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime());
    }

    public static long bootTime() {
        BridgeContext context = getCurrentContext();
        if (context != null) {
            return context.getSessionInteractiveData().getBootNanosTime();
        }
        return 0;
    }

    public static long bootTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(bootTime());
    }

    // This is no-op since layoutlib infrastructure loads all the native libraries.
    public static void loadLibrary(String libname) {
        // ignore.
    }
}
