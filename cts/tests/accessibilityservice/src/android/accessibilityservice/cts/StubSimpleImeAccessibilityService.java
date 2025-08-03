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

package android.accessibilityservice.cts;

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.InputMethod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class StubSimpleImeAccessibilityService extends InstrumentedAccessibilityService {
    private final CountDownLatch mOnCreateInputMethodLatch = new CountDownLatch(1);

    public boolean awaitOnCreateInputMethod(long timeout, TimeUnit unit)
            throws InterruptedException {
        return mOnCreateInputMethodLatch.await(timeout, unit);
    }

    @Override
    public InputMethod onCreateInputMethod() {
        mOnCreateInputMethodLatch.countDown();
        return super.onCreateInputMethod();
    }
}
