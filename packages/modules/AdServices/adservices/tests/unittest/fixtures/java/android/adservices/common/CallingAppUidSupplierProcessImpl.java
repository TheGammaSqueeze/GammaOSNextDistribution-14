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

package android.adservices.common;

import android.os.Process;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.common.CallingAppUidSupplier;

/** This class is meant to be used in tests to get the calling app UID. */
@VisibleForTesting
public class CallingAppUidSupplierProcessImpl implements CallingAppUidSupplier {
    /** Creates an instance of {@link CallingAppUidSupplierProcessImpl} */
    public static CallingAppUidSupplierProcessImpl create() {
        return new CallingAppUidSupplierProcessImpl();
    }

    @Override
    public int getCallingAppUid() {
        return Process.myUid();
    }
}
