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

package android.provider;

import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.content.ContentResolver;

/**
 * Delegate that provides alternative implementation for methods in {@link Settings.Config}
 * <p/>
 * Through the layoutlib_create tool, selected methods of DeviceConfig have been replaced by
 * calls to methods of the same name in this delegate class.
 */
public class Settings_Config_Delegate {
    @LayoutlibDelegate
    static ContentResolver getContentResolver() {
        return RenderAction.getCurrentContext().getContentResolver();
    }
}
