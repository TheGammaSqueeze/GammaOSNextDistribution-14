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
import android.annotation.SystemApi;

/**
 * A blueprint for the system ui proxy which is meant to host all the system ui interaction that is
 * required by other apps.
 * @hide
 */
@SystemApi
public interface CarSystemUIProxy {
    /**
     * Creates the host side of the task view and links the provided {@code carTaskViewClient}
     * to the same.
     * This method will be deprecated in Android 15. Please use
     * {@link CarSystemUIProxy#createCarTaskView(CarTaskViewClient)} instead.
     * @return a handle to the host side of task view.
     */
    @NonNull
    CarTaskViewHost createControlledCarTaskView(@NonNull CarTaskViewClient carTaskViewClient);

    /**
     * Creates the host side of the task view and links the provided {@code
     * carTaskViewClient} to the same.
     * @return a handle to the host side of task view.
     *
     * @hide
     */
    @NonNull
    CarTaskViewHost createCarTaskView(@NonNull CarTaskViewClient carTaskViewClient);
}
