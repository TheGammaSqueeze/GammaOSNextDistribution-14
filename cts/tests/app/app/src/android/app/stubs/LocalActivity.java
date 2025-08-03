/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.stubs;

import android.os.Bundle;

import java.util.concurrent.atomic.AtomicReference;

public class LocalActivity extends TestedActivity {
    private static final AtomicReference<String> sLastAttributionTag = new AtomicReference<>();

    public LocalActivity() {
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        sLastAttributionTag.set(getAttributionTag());
    }

    public static String getAndClearLastAttributionTag() {
        return sLastAttributionTag.getAndSet(null);
    }
}
