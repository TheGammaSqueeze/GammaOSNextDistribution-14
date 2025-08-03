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

package android.car.builtin.window;

import android.annotation.SystemApi;
import android.window.DisplayAreaOrganizer;

/**
 * Helper for {@link android.window.DisplayAreaOrganizer}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class DisplayAreaOrganizerHelper {
    /**
     * The value in display area indicating that no value has been set.
     */
    public static final int FEATURE_UNDEFINED = DisplayAreaOrganizer.FEATURE_UNDEFINED;
}
