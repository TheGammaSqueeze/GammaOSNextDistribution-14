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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests SandboxedSdkProvider. */
@RunWith(JUnit4.class)
public class SandboxedSdkProviderTest extends SandboxKillerBeforeTest {

    private SandboxedSdkProvider mSdk;

    @Before
    public void setup() {
        mSdk = new TestSandboxedSdkProvider();
    }

    @Test
    public void testAttachContext_returnsNullWhenContextNotAttached() throws Exception {
        assertThat(mSdk.getContext()).isNull();
    }

    @Test
    public void testAttachContext_returnsSameContextThatWasAttached() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdk.attachContext(context);
        assertThat(mSdk.getContext()).isSameInstanceAs(context);
    }

    @Test
    public void testAttachContext_cannotAttachNullContext() throws Exception {
        final NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> mSdk.attachContext(null));
        assertThat(thrown).hasMessageThat().contains("Context cannot be null");
    }

    @Test
    public void testAttachContext_cannotAttachMultipleTimes() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdk.attachContext(context);
        final IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> mSdk.attachContext(context));
        assertThat(thrown).hasMessageThat().contains("Context already set");
    }

    private static class TestSandboxedSdkProvider extends SandboxedSdkProvider {

        @Override
        public SandboxedSdk onLoadSdk(Bundle params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void beforeUnloadSdk() {}

        @Override
        public View getView(Context windowContext, Bundle params, int width, int height) {
            throw new UnsupportedOperationException();
        }
    }
}
