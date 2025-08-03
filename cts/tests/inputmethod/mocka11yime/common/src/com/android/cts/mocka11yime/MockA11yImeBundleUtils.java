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

package com.android.cts.mocka11yime;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

final class MockA11yImeBundleUtils {
    /**
     * Not intended to be instantiated.
     */
    private MockA11yImeBundleUtils() {
    }

    static void dumpBundle(@NonNull StringBuilder sb, @NonNull Bundle bundle) {
        sb.append('{');
        boolean first = true;
        for (String key : bundle.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            final Object object = bundle.get(key);
            sb.append(key);
            sb.append('=');
            if (object instanceof EditorInfo) {
                final EditorInfo info = (EditorInfo) object;
                sb.append("EditorInfo{packageName=").append(info.packageName);
                sb.append(" fieldId=").append(info.fieldId);
                sb.append(" hintText=").append(info.hintText);
                sb.append(" privateImeOptions=").append(info.privateImeOptions);
                sb.append("}");
            } else if (object instanceof Bundle) {
                dumpBundle(sb, (Bundle) object);
            } else {
                sb.append(object);
            }
        }
        sb.append('}');
    }
}
