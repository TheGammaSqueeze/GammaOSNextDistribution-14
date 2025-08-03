/*
 * Copyright (C) 2019 The Android Open Source Project
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

package dalvik.system;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

/**
 * Delegate used to provide implementation of a select few native methods of {@link VMRuntime}
 * <p/>
 * Through the layoutlib_create tool, the original native methods of VMRuntime have been replaced
 * by calls to methods of the same name in this delegate class.
 */
public class VMRuntime_Delegate {

    @LayoutlibDelegate
    /*package*/ static Object newUnpaddedArray(VMRuntime runtime, Class<?> componentType,
            int minLength) {
        return VMRuntimeCommonHelper.newUnpaddedArray(runtime, componentType, minLength);
    }

    @LayoutlibDelegate
    /*package*/ static int getNotifyNativeInterval() {
        return VMRuntimeCommonHelper.getNotifyNativeInterval();
    }

    @LayoutlibDelegate
    public static boolean is64Bit(VMRuntime runtime) {
        return true;
    }
}
