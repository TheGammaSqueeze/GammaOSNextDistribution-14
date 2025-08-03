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

package android.car.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Application;
import android.car.builtin.app.ActivityManagerHelper;
import android.os.Bundle;

/**
 * A factory to create instances of the {@link CarTaskViewControllerHostLifecycle}.
 *
 * @hide
 */
public final class CarTaskViewControllerHostLifecycleFactory {
    private CarTaskViewControllerHostLifecycleFactory() {
    }

    /**
     * Creates an instance of {@link CarTaskViewControllerHostLifecycle} which adapts to the
     * activity lifecycle.
     *
     * @param activity the activity which the {@link CarTaskViewControllerHostLifecycle} needs to
     *                 be created for.
     */
    @NonNull
    public static CarTaskViewControllerHostLifecycle forActivity(@NonNull Activity activity) {
        return new CarTaskViewControllerHostActivityLifecycleAdapter(activity).getLifecycle();
    }

    private static class CarTaskViewControllerHostActivityLifecycleAdapter
                implements Application.ActivityLifecycleCallbacks {

        CarTaskViewControllerHostLifecycle mCarTaskViewControllerHostLifecycle;

        CarTaskViewControllerHostActivityLifecycleAdapter(Activity activity) {
            mCarTaskViewControllerHostLifecycle = new CarTaskViewControllerHostLifecycle();
            activity.registerActivityLifecycleCallbacks(this);
            // If the activity is already in resumed state, trigger the host appeared callback
            // so that the visibility information is latest.
            if (ActivityManagerHelper.isVisible(activity)) {
                mCarTaskViewControllerHostLifecycle.hostAppeared();
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity,
                    @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            // Don't invoke hostAppeared() in onStart(), which breaks the CTS
            // ActivityLifecycleTests#testFinishBelowDialogActivity.
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            mCarTaskViewControllerHostLifecycle.hostAppeared();
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            mCarTaskViewControllerHostLifecycle.hostDisappeared();
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity,
                    @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            mCarTaskViewControllerHostLifecycle.hostDestroyed();
        }

        public CarTaskViewControllerHostLifecycle getLifecycle() {
            return mCarTaskViewControllerHostLifecycle;
        }
    }
}
