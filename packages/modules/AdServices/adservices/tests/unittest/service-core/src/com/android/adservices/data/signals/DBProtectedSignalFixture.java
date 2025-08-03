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

package com.android.adservices.data.signals;

import android.adservices.common.CommonFixture;

import java.time.Duration;

public class DBProtectedSignalFixture {

    public static final byte[] KEY = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
    public static final byte[] VALUE = {(byte) 42};

    public static DBProtectedSignal.Builder getBuilder() {
        return DBProtectedSignal.builder()
                .setId(null)
                .setBuyer(CommonFixture.VALID_BUYER_1)
                .setKey(DBProtectedSignalFixture.KEY)
                .setValue(DBProtectedSignalFixture.VALUE)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    public static final DBProtectedSignal SIGNAL = getBuilder().build();
    public static final DBProtectedSignal LATER_TIME_SIGNAL =
            getBuilder()
                    .setCreationTime(
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(Duration.ofDays(1)))
                    .build();
}
