/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.content.wm.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Test for {@link Context#isUiContext}.
 * <p>Test context type listed below:</p>
 * <ul>
 *     <li>{@link android.app.Application} - returns {@code false}</li>
 *     <li>{@link Service} - returns {@code false}</li>
 *     <li>{@link Activity} - returns {@code true}</li>
 *     <li>Context via {@link Context#createWindowContext(int, Bundle)} - returns {@code true}</li>
 *     <li>Context via {@link android.inputmethodservice.InputMethodService}
 *     - returns {@code true}</li>
 *     <li>Context via {@link Context#createDisplayContext(Display)} - returns {@code false}</li>
 *     <li>Context derived from display context - returns {@code false}</li>
 *      <li>UI derived context - returns {@code true}</li>
 *     <li>UI derived display context - returns {@code false}</li>
 *     <li>{@link ContextWrapper} with base UI {@link Context} - returns {@code true}</li>
 *     <li>{@link ContextWrapper} with base non-UI {@link Context} - returns {@code false}</li>
 *     <li>{@link android.window.WindowProviderService} - returns {@code true}</li>
 * </ul>
 *
 * <p>Build/Install/Run:
 *     atest CtsContentTestCases:ContextIsUiContextTest
 */
@Presubmit
@SmallTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContextIsUiContextTest extends ContextTestBase {
    @Test
    public void testIsUiContextOnApplication() {
        assertThat(mApplicationContext.isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextOnService() throws Exception {
        assertThat(createTestService().isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextOnActivity() throws Throwable {
        assertThat(getTestActivity().isUiContext()).isTrue();
    }

    @Test
    public void testIsUiContextOnWindowContext() {
        assertThat(createWindowContext().isUiContext()).isTrue();
    }

    @Test
    public void testIsUiContextOnWindowContextWithDisplay() {
        final Context windowContext = mApplicationContext.createWindowContext(getDefaultDisplay(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null /* options */);
        assertThat(windowContext.isUiContext()).isTrue();
    }

    @Test
    public void testIsUiContextOnDefaultDisplayContext() {
        final Context defaultDisplayContext =
                mApplicationContext.createDisplayContext(getDefaultDisplay());
        assertThat(defaultDisplayContext.isUiContext()).isFalse();

        final Context defaultDisplayDerivedContext = defaultDisplayContext
                .createAttributionContext(null /* attributionTag */);
        assertThat(defaultDisplayDerivedContext.isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextOnSecondaryDisplayContext() {
        final Context secondaryDisplayContext =
                mApplicationContext.createDisplayContext(getSecondaryDisplay());
        assertThat(secondaryDisplayContext.isUiContext()).isFalse();

        final Context secondaryDisplayDerivedContext = secondaryDisplayContext
                .createAttributionContext(null /* attributionTag */);
        assertThat(secondaryDisplayDerivedContext.isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextOnUiDerivedContext() {
        final Context uiDerivedContext = createWindowContext()
                .createAttributionContext(null /* attributionTag */);
        assertThat(uiDerivedContext.isUiContext()).isTrue();
    }

    @Test
    public void testIsUiContextOnUiDerivedDisplayContext() {
        final Context uiDerivedDisplayContext = createWindowContext()
                .createDisplayContext(getSecondaryDisplay());
        assertThat(uiDerivedDisplayContext.isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextOnUiContextWrapper() {
        final Context uiContextWrapper = new ContextWrapper(createWindowContext());
        assertThat(uiContextWrapper.isUiContext()).isTrue();
    }

    @Test
    public void testIsUiContextOnNonUiContextWrapper() {
        final Context uiContextWrapper = new ContextWrapper(mApplicationContext);
        assertThat(uiContextWrapper.isUiContext()).isFalse();
    }

    @Test
    public void testIsUiContextFromWindowProviderService() {
        assertThat(createWindowTestService().isUiContext()).isTrue();
    }
}
