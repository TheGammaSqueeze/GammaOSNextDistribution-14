/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity.mdns.util;

import android.annotation.Nullable;
import android.text.TextUtils;

import com.android.net.module.util.SharedLog;

/**
 * The logger used in mDNS.
 */
public class MdnsLogger {
    // Make this logger public for other level logging than dogfood.
    public final SharedLog mLog;

    /**
     * Constructs a new {@link MdnsLogger} with the given logging tag.
     *
     * @param tag The log tag that will be used by this logger
     */
    public MdnsLogger(String tag) {
        mLog = new SharedLog(tag);
    }

    public void log(String message) {
        mLog.log(message);
    }

    public void log(String message, @Nullable Object... args) {
        mLog.log(message + " ; " + TextUtils.join(" ; ", args));
    }

    public void d(String message) {
        mLog.log(message);
    }

    public void e(String message) {
        mLog.e(message);
    }

    public void e(String message, Throwable e) {
        mLog.e(message, e);
    }

    public void w(String message) {
        mLog.w(message);
    }
}
