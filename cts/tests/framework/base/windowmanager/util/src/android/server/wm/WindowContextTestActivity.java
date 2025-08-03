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

package android.server.wm;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Configuration;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WindowContextTestActivity extends WindowManagerTestBase.FocusableActivity {
    final CountDownLatch mLatch = new CountDownLatch(1);

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLatch.countDown();
    }

    public void waitAndAssertConfigurationChanged() {
        try {
            assertThat(mLatch.await(4, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
