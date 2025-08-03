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

package com.android.media.videoquality.bdrate;

/**
 * {@link RuntimeException} thrown when the BD Rate Calculator cannot calculate BD Rate due to a
 * precondition failure, which include:
 *
 * <ul>
 *   <li>Not enough rate-distortion points after clustering.
 *   <li>A non-monotonically increasing rate-distortion curve.
 *   <li>Non-overlapping rate-distortion curves.
 * </ul>
 */
public class BdPreconditionFailedException extends RuntimeException {
    private final boolean mIsTargetCurve;

    public BdPreconditionFailedException(String message, boolean isTargetCurve) {
        super(message);
        mIsTargetCurve = isTargetCurve;
    }

    public boolean isTargetCurve() {
        return mIsTargetCurve;
    }
}
