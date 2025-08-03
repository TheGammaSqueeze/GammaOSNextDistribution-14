/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.cts.tagging.sdk30;

import android.app.Activity;
import android.cts.tagging.Utils;
import android.os.Bundle;

public class HeapZeroInitMemtagAsyncActivity extends Activity {
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (!Utils.allocatorIsScudo()) {
            // jemalloc doesn't support heap zero initialization. Skip this test.
            setResult(TestActivity.RESULT_TEST_IGNORED);
        } else if (Utils.heapIsZeroInitialized()) {
            setResult(TestActivity.RESULT_TEST_SUCCESS);
        } else {
            setResult(TestActivity.RESULT_TEST_FAILED);
        }

        finish();
    }
}
