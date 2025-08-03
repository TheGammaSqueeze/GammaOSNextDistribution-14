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

package com.android.cts.verifier.presence.ble;

import android.os.ParcelUuid;

import java.util.UUID;

public class Const {
    private static final String TAG = BleScanner.class.getName();
    public static final String UUID_STRING = "CDB7950D-73F1-4D4D-8E47-C090502DBD63";
    public static final ParcelUuid PARCEL_UUID = new ParcelUuid(UUID.fromString(UUID_STRING));
}
