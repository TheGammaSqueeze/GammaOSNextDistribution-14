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

package android.view.inputmethod.cts;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.UnlockScreenRule;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class PackageVisibilityTest extends EndToEndImeTestBase {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.PackageVisibilityTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @AppModeFull
    @Test
    public void testTargetPackageIsVisibleFromImeFull() throws Exception {
        testTargetPackageIsVisibleFromIme(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testTargetPackageIsVisibleFromImeInstant() throws Exception {
        testTargetPackageIsVisibleFromIme(true /* instant */);
    }

    private void testTargetPackageIsVisibleFromIme(boolean instant) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            try (AutoCloseable closeable = MockTestActivityUtil.launchSync(instant,
                    TIMEOUT, Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker))) {
                expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

                final ImeCommand command = imeSession.callGetApplicationInfo(
                        MockTestActivityUtil.getPackageName(), PackageManager.GET_META_DATA);
                final ImeEvent event = expectCommand(stream, command, TIMEOUT);

                if (event.isNullReturnValue()) {
                    fail("getApplicationInfo() returned null.");
                }
                if (event.isExceptionReturnValue()) {
                    final Exception exception = event.getReturnExceptionValue();
                    fail(exception.toString());
                }
                final ApplicationInfo applicationInfoFromIme = event.getReturnParcelableValue();
                assertEquals(MockTestActivityUtil.getPackageName(),
                        applicationInfoFromIme.packageName);
            }
        }
    }
}
