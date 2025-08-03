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
 * Vibration scale for one or more vibration types
 */
@VintfStability
parcelable ScaleParam {
    const int TYPE_ALARM = 1 << 0; // Alarm usage
    const int TYPE_NOTIFICATION = 1 << 1; // Notification and communication request usages
    const int TYPE_RINGTONE = 1 << 2; // Ringtone usage
    const int TYPE_INTERACTIVE = 1 << 3; // Touch and hardware feedback usages
    const int TYPE_MEDIA = 1 << 4; // Media and unknown usages

    // combined bitfield of ScaleParam::TYPE* values
    int typesMask;
    float scale;
}