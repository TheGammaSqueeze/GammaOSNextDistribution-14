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
package android.server.wm.other;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.WindowManager;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Presubmit
public class WindowManagerTests {

    @Test
    @ApiTest(apis = {"android.view.WindowManager#addProposedRotationListener",
            "android.view.WindowManager#removeProposedRotationListener"})
    public void testProposedRotationListener() throws InterruptedException {
        final Context context = getInstrumentation().getContext();
        final Display display = Objects.requireNonNull(
                context.getSystemService(DisplayManager.class)).getDisplay(Display.DEFAULT_DISPLAY);
        final Context windowContext = context.createWindowContext(display,
                TYPE_APPLICATION_OVERLAY, null /* options */);
        final CountDownLatch latch = new CountDownLatch(1);
        final WindowManager wm = Objects.requireNonNull(
                windowContext.getSystemService(WindowManager.class));
        final IntConsumer listener = rotation -> latch.countDown();
        wm.addProposedRotationListener(context.getMainExecutor(), listener);
        try {
            assertTrue("Timed out while waiting for proposed rotation to be received",
                    latch.await(5, TimeUnit.SECONDS));
        } finally {
            wm.removeProposedRotationListener(listener);
        }
    }
}
