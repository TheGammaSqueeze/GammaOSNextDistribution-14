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

package android.os;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.os.SystemProperties.Handle;

public class SystemProperties_Delegate {
    @LayoutlibDelegate
    public static Handle find(String name) {
        // The native method called by the original version of SystemProperties.find
        // throws a fatal exception for non-bionic devices.
       return null;
    }
}
