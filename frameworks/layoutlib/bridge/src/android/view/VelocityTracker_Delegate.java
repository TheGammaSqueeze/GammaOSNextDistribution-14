/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import static android.view.VelocityTracker.VELOCITY_TRACKER_STRATEGY_IMPULSE;

public class VelocityTracker_Delegate {

    @LayoutlibDelegate
    public static VelocityTracker obtain() {
        // Default VelocityTracker tries to use ApplicationThread which is not supported
        // by layoutlib. Specify a strategy to work around this issue.
        return VelocityTracker.obtain(VELOCITY_TRACKER_STRATEGY_IMPULSE);
    }

    @LayoutlibDelegate
    public static VelocityTracker obtain(String strategy) {
        return VelocityTracker.obtain_Original(strategy);
    }

    @LayoutlibDelegate
    public static VelocityTracker obtain(int strategy) {
        return VelocityTracker.obtain_Original(strategy);
    }
}
