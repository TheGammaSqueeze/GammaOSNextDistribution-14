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

package com.android.server.nearby.presence;

import android.nearby.BroadcastRequest;

/**
 * Provides serialization and deserialization util methods for {@link FastAdvertisement}.
 */
public final class FastAdvertisementUtils {

    private static final int VERSION_MASK = 0b11100000;

    private static final int IDENTITY_TYPE_MASK = 0b00001110;

    /**
     * Constructs the header of a {@link FastAdvertisement}.
     */
    public static byte constructHeader(@BroadcastRequest.BroadcastVersion int version,
            int identityType) {
        return (byte) (((version << 5) & VERSION_MASK) | ((identityType << 1)
                & IDENTITY_TYPE_MASK));
    }

    private FastAdvertisementUtils() {}
}
