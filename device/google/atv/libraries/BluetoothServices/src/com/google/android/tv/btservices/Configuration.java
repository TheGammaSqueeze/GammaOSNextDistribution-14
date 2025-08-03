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

package com.google.android.tv.btservices;

import android.content.Context;
import android.content.res.Resources;

public final class Configuration {

    private static final Object LOCK  = new Object();
    private static Configuration mInst;

    private Resources mResources;

    public static Configuration get(Context context) {
        synchronized(LOCK) {
            if (mInst != null) {
              return mInst;
            }
            mInst = new Configuration(context);
            return mInst;
        }
    }

    private Configuration(Context context) {
        mResources = context.getResources();
    }

    public boolean isEnabled(int config) {
        return mResources.getBoolean(config);
    }
}
