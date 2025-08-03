/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.view.inputmethod;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;


/**
 * Delegate used to provide new implementation of a select few methods of {@link InputMethodManager}
 *
 * Through the layoutlib_create tool, the original  methods of InputMethodManager have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 */
public class InputMethodManager_Delegate {

    // ---- Overridden methods ----

    @LayoutlibDelegate
    /*package*/ static boolean isInEditMode() {
        return true;
    }


    @LayoutlibDelegate
    /*package*/ static boolean showSoftInput(InputMethodManager thisManager, View view, int flags) {
        return false;
    }

    @LayoutlibDelegate
    /*package*/ static boolean showSoftInput(InputMethodManager thisManager, View view, int flags,
            ResultReceiver resultReceiver) {
        return false;
    }

    @LayoutlibDelegate
    /*package*/static boolean showSoftInput(InputMethodManager thisManager, View view,
            ImeTracker.Token statsToken, int flags, ResultReceiver resultReceiver, int reason) {
        return false;
    }

    @LayoutlibDelegate
    /*package*/ static boolean hideSoftInputFromWindow(InputMethodManager thisManager,
            IBinder windowToken, int flags) {
        return false;
    }

    @LayoutlibDelegate
    /*package*/ static boolean hideSoftInputFromWindow(InputMethodManager thisManager,
            IBinder windowToken, int flags, ResultReceiver resultReceiver) {
        return false;
    }

    @LayoutlibDelegate
    /*package*/ static boolean hideSoftInputFromWindow(InputMethodManager thisManager,
            IBinder windowToken, int flags, ResultReceiver resultReceiver, int reason) {
        return false;
    }
}
