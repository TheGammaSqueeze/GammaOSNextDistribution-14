/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

/** WifiLog implementation that does nothing. */
public class FakeWifiLog implements WifiLog {
    private static final NoLogMessage NO_LOG_MESSAGE = new NoLogMessage();

    // New-style methods.
    @Override
    public LogMessage err(String format) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public LogMessage warn(String format) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public LogMessage info(String format) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public LogMessage trace(String format) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public LogMessage trace(String format, int numFramesToIgnore) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public LogMessage dump(String format) {
        return NO_LOG_MESSAGE;
    }

    @Override
    public void eC(String msg) {
        // Do nothing.
    }

    @Override
    public void wC(String msg) {
        // Do nothing.
    }

    @Override
    public void iC(String msg) {
        // Do nothing.
    }

    @Override
    public void tC(String msg) {
        // Do nothing.
    }

    // Legacy methods.
    @Override
    public void e(String msg) {
        // Do nothing.
    }

    @Override
    public void w(String msg) {
        // Do nothing.
    }

    @Override
    public void i(String msg) {
        // Do nothing.
    }

    @Override
    public void d(String msg) {
        // Do nothing.
    }

    @Override
    public void v(String msg) {
        // Do nothing.
    }
}
