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

package android.frameworks.vibrator;

/**
 * IVibratorController is an interface that allows clients to request the VibrationParams for the
 * specified vibration types. If the request is longer than the specified deadline, the result will
 * be ignored by the caller.
 * @hide
 */
@VintfStability
interface IVibratorController {
    /**
     * Triggers a request to receive VibrationParams for the specified vibration types. The
     * received params are used to modify vibration characteristics, such as scaling.
     * <p> If the request takes longer than the specified deadline, the request must be ignored by
     * the caller.
     *
     * @param typesMask                     The combined bitfield of vibration types queried
     * @param deadlineElapsedRealtimeMillis The request deadline, result ignored after this
     * @param requestToken                  The token for the async result, used by the service
     */
    oneway void requestVibrationParams(in int typesMask,
            in long deadlineElapsedRealtimeMillis, in IBinder requestToken);
}