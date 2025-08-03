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

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.cts.MockActivity;
import android.content.cts.MockService;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Binder;
import android.os.IBinder;
import android.view.Display;
import android.window.WindowProviderService;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;

import java.util.concurrent.TimeoutException;

/**
 * Used for providing various kinds of contexts. This test base often used for verifying APIs
 * which have different behaviors on different kinds of {@link Context}s
 */
public class ContextTestBase {
    public Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private Display mDefaultDisplay;
    private VirtualDisplay mSecondaryDisplay;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public final ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setUp() {
        final DisplayManager dm = mApplicationContext.getSystemService(DisplayManager.class);
        mDefaultDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        mSecondaryDisplay = createSecondaryDisplay();
    }

    private VirtualDisplay createSecondaryDisplay() {
        final DisplayManager displayManager = mApplicationContext
                .getSystemService(DisplayManager.class);
        final int width = 800;
        final int height = 480;
        final int density = 160;
        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888,
                2 /* maxImages */);
        VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay(
                ContextTestBase.class.getName(), width, height, density, reader.getSurface(),
                VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        return virtualDisplay;
    }

    public Display getDefaultDisplay() {
        return mDefaultDisplay;
    }

    public Display getSecondaryDisplay() {
        return mSecondaryDisplay.getDisplay();
    }

    public void resizeSecondaryDisplay(int width, int height, int densityDpi) {
        mSecondaryDisplay.resize(width, height, densityDpi);
    }

    public Context createWindowContext() {
        return mApplicationContext.createDisplayContext(mDefaultDisplay).createWindowContext(
                TYPE_APPLICATION_OVERLAY, null /* options */);
    }

    public Activity getTestActivity() throws Throwable {
        MockActivity[] activity = new MockActivity[1];
        mActivityRule.runOnUiThread(() -> activity[0] = mActivityRule.getActivity());
        return activity[0];
    }

    public Service createTestService() throws TimeoutException {
        final Intent intent = new Intent(mApplicationContext, MockService.class);
        final ServiceTestRule serviceRule = new ServiceTestRule();
        IBinder serviceToken;
        serviceToken = serviceRule.bindService(intent);
        return ((MockService.MockBinder) serviceToken).getService();
    }

    public Service createWindowTestService() {
        final Intent intent = new Intent(mApplicationContext, TestWindowService.class);
        final ServiceTestRule serviceRule = new ServiceTestRule();
        try {
            IBinder serviceToken = serviceRule.bindService(intent);
            return ((TestWindowService.TestToken) serviceToken).getService();
        } catch (TimeoutException e) {
            fail("Test fail because of " + e);
            return null;
        }
    }

    public static class TestWindowService extends WindowProviderService {
        private final IBinder mToken = new TestToken();

        @Override
        public IBinder onBind(Intent intent) {
            return mToken;
        }

        @Override
        public int getWindowType() {
            return TYPE_APPLICATION_OVERLAY;
        }

        public class TestToken extends Binder {
            private TestWindowService getService() {
                return TestWindowService.this;
            }
        }
    }
}
