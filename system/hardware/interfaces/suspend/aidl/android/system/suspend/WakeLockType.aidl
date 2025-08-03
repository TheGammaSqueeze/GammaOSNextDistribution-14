/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.system.suspend;

/**
 * Blocking CPU suspend is the only constraint that must be respected by all
 * wake lock types. E.g. a request for a full wake lock must block CPU suspend,
 * but not necessarily keep the screen alive.
 */
@VintfStability
enum WakeLockType {
    /* CPU stays on. */
    PARTIAL = 0,
    /* CPU and the screen stay on. */
    FULL = 1,
}
