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

package com.android.layoutlib.bridge.intensive;

import com.android.utils.ILogger;
import com.android.layoutlib.bridge.intensive.setup.LayoutlibBridgeClientCallback;

public class LayoutLibTestCallback extends LayoutlibBridgeClientCallback {
    private static final String S_PACKAGE_NAME = "com.android.layoutlib.test.myapplication";

    public LayoutLibTestCallback(ILogger logger, ClassLoader classLoader) {
        super(logger, classLoader, S_PACKAGE_NAME);
    }
}
