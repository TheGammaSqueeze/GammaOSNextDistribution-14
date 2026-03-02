/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.display;

import android.content.Context;
import android.graphics.Point;
import android.os.RemoteException;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class CustomResolutionPreferenceController extends BasePreferenceController {

    private final IWindowManager mWindowManager;

    public CustomResolutionPreferenceController(Context context, String key) {
        super(context, key);
        mWindowManager = WindowManagerGlobal.getWindowManagerService();
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        Point initialSize = new Point();
        Point baseSize = new Point();
        try {
            mWindowManager.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initialSize);
            mWindowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
        } catch (RemoteException e) {
            return null;
        }

        if (initialSize.equals(baseSize)) {
            return mContext.getString(R.string.custom_resolution_summary,
                    baseSize.x, baseSize.y);
        } else {
            return mContext.getString(R.string.custom_resolution_summary_custom,
                    baseSize.x, baseSize.y);
        }
    }
}
