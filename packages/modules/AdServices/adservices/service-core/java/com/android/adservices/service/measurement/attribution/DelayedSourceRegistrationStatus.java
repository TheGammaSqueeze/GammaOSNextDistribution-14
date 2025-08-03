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

package com.android.adservices.service.measurement.attribution;

/** POJO for storing delayed source registration status */
public class DelayedSourceRegistrationStatus {
    public final int UNKNOWN = 0;

    private long mRegistrationDelay;

    public DelayedSourceRegistrationStatus() {}

    /** Get registration delay. */
    public long getRegistrationDelay() {
        return mRegistrationDelay;
    }

    /** Set registration delay. */
    public void setRegistrationDelay(long registrationDelay) {
        mRegistrationDelay = registrationDelay;
    }
}
