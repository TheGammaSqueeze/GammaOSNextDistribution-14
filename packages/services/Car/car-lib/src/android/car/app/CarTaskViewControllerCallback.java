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
 * Callback interface required to monitor the lifecycle of {@link CarTaskViewController}.
 * @hide
 */
@SystemApi
public interface CarTaskViewControllerCallback {
    /**
     * Called when the {@code carTaskViewController} is connected.
     */
    void onConnected(@NonNull CarTaskViewController carTaskViewController);

    /**
     * Called when the {@code carTaskViewController} is disconnected.
     */
    void onDisconnected(@NonNull CarTaskViewController carTaskViewController);
}
