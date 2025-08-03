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

package com.android.cobalt.system;

/**
 * Testable wrapper around {@link System} and {@link android.os.SystemClock}.
 *
 * <p>In tests, pass an instance of FakeSystemClock, which allows you to control the values returned
 * by the methods below.
 *
 * <p>Copied from
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/packages/SystemUI/src/com/android/systemui/util/time/SystemClock.java
 */
public interface SystemClock {
    /**
     * @see System#currentTimeMillis()
     */
    long currentTimeMillis();
}
