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

package com.android.server.uwb;

import android.os.Bundle;
import android.util.Log;

import androidx.test.runner.AndroidJUnitRunner;

import java.lang.reflect.Method;

public class CustomTestRunner extends AndroidJUnitRunner {
    @Override
    public void onCreate(Bundle arguments) {
        // Override the default TerribleFailureHandler, as that handler might terminate
        // the process (if we're on an eng build).
        // Use reflection since we are compiling the tests against SDK and |setWtfHandler| is @hide.
        try {
            Class<Log> clazz = Log.class;
            Method method = clazz.getMethod("setWtfHandler", Log.TerribleFailureHandler.class);
            Log.TerribleFailureHandler handler = (tag, what, system) -> Log.e(tag, "WTF", what);
            method.invoke(null, handler);
        } catch (Exception e) {
            Log.e("CustomTestRunner", "Failed to set wtf handler", e);
        }
        super.onCreate(arguments);
    }
}
