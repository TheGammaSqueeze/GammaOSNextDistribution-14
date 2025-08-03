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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.concurrent.TimeoutException;

/**
 * Test for {@link Context#getDisplay()}.
 * <p>Test context type listed below:</p>
 * <ul>
 *     <li>{@link android.app.Application} - throw exception</li>
 *     <li>{@link Service} - throw exception</li>
 *     <li>{@link Activity} - get {@link Display} entity</li>
 *     <li>Context via {@link Context#createWindowContext(int, Bundle)}
 *     - get {@link Display} entity</li>
 *     <li>Context via {@link Context#createDisplayContext(Display)}
 *     - get {@link Display} entity</li>
 *     <li>Context derived from display context
 *     - get {@link Display} entity</li>
 *     <li>{@link ContextWrapper} with base display-associated {@link Context}
 *     - get {@link Display} entity</li>
 *     <li>{@link ContextWrapper} with base non-display-associated {@link Context}
 *     - get {@link Display} entity</li>
 *     <li>{@link android.window.WindowProviderService} - get {@link Display} entity</li>
 * </ul>
 *
 * <p>Build/Install/Run:
 *     atest CtsContentTestCases:ContextGetDisplayTest
 */
@Presubmit
@SmallTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContextGetDisplayTest extends ContextTestBase {
    @Test(expected = UnsupportedOperationException.class)
    public void testGetDisplayFromApplication() {
        mApplicationContext.getDisplay();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDisplayFromService() throws TimeoutException {
        createTestService().getDisplay();
    }

    @Test
    public void testGetDisplayFromActivity() throws Throwable {
        final Display d = getTestActivity().getDisplay();

        assertNotNull("Display must be accessible from visual components", d);
    }

    @Test
    public void testGetDisplayFromDisplayContext() {
        final Display display = getDefaultDisplay();
        Context displayContext = mApplicationContext.createDisplayContext(display);

        assertEquals(display, displayContext.getDisplay());
    }

    @Test
    public void testGetDisplayFromDisplayContextDerivedContextOnPrimaryDisplay() {
        verifyGetDisplayFromDisplayContextDerivedContext(false /* onSecondaryDisplay */);
    }

    @Test
    public void testGetDisplayFromDisplayContextDerivedContextOnSecondaryDisplay() {
        verifyGetDisplayFromDisplayContextDerivedContext(true /* onSecondaryDisplay */);
    }

    private void verifyGetDisplayFromDisplayContextDerivedContext(boolean onSecondaryDisplay) {
        final Display display = onSecondaryDisplay ? getSecondaryDisplay() : getDefaultDisplay();
        final Context context = mApplicationContext.createDisplayContext(display)
                .createConfigurationContext(new Configuration());
        assertEquals(display, context.getDisplay());
    }

    @Test
    public void testGetDisplayFromWindowContext() {
        final Display display = getDefaultDisplay();
        final Context windowContext = createWindowContext();
        assertEquals(display.getDisplayId(), windowContext.getDisplay().getDisplayId());
    }

    @Test
    public void testGetDisplayFromWindowContextWithDefaultDisplay() {
        final Display display = getDefaultDisplay();
        final Context windowContext = mApplicationContext.createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null /* options */);
        assertEquals(display.getDisplayId(), windowContext.getDisplay().getDisplayId());
    }

    @Test
    public void testGetDisplayFromWindowContextWithSecondaryDisplay() {
        final Display display = getSecondaryDisplay();
        final Context windowContext = mApplicationContext.createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null /* options */);
        assertEquals(display.getDisplayId(), windowContext.getDisplay().getDisplayId());
    }

    @Test
    public void testGetDisplayFromVisualWrapper() throws Throwable {
        final Activity activity = getTestActivity();
        final Display d = new ContextWrapper(activity).getDisplay();

        assertEquals("Displays between context wrapper and base UI context must match.",
                activity.getDisplay(), d);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDisplayFromNonVisualWrapper() {
        ContextWrapper wrapper = new ContextWrapper(mApplicationContext);
        wrapper.getDisplay();
    }

    @Test
    public void testGetDisplayFromWindowProviderService() {
        final Display d = createWindowTestService().getDisplay();

        assertNotNull("Display must be accessible from visual components", d);
    }
}
