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

import android.annotation.Nullable;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.List;

public class BundleCompat {
    public BundleCompat(Bundle bundle) {
        mBundle = bundle;
    }

    private final Bundle mBundle;

    public void putParcelableList(String key, List<? extends Parcelable> value) {
        mBundle.putParcelableList(key, value);
    }

    public void putObject(@Nullable String key, @Nullable Object value) {
        mBundle.putObject(key, value);
    }
}
