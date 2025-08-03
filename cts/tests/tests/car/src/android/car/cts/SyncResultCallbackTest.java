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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.SyncResultCallback;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// TODO(b/269040634): Add interrupt exception tests for get call.
public final class SyncResultCallbackTest {

    private static final String KEY = "testKey";
    private static final double VALUE = 1.75;

    private final SyncResultCallback<Bundle> mCallback = new SyncResultCallback<Bundle>();

    private final Bundle mBundle = new Bundle();

    @Before
    public void setup() {
        mBundle.putDouble(KEY, VALUE);
    }

    @Test
    public void testGet() throws Exception {
        mCallback.onResult(mBundle);

        assertThat(mCallback.get().getDouble(KEY)).isEqualTo(VALUE);
    }

    @Test
    public void testGetWithTimeout() throws Exception {
        mCallback.onResult(mBundle);

        assertThat(mCallback.get(1000, TimeUnit.MILLISECONDS).getDouble(KEY)).isEqualTo(VALUE);
    }

    @Test
    public void testGetTimeoutException() throws Exception {
        assertThrows(TimeoutException.class,
                () -> mCallback.get(100, TimeUnit.MILLISECONDS));
    }
}
