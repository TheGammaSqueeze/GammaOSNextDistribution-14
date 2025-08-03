/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.isInputMethodPickerShown;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.SecurityTest;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodManagerTest {
    private static final String MOCK_IME_ID = "com.android.cts.mockime/.MockIme";
    private static final String MOCK_IME_LABEL = "Mock IME";
    private static final String HIDDEN_FROM_PICKER_IME_ID =
            "com.android.cts.hiddenfrompickerime/.HiddenFromPickerIme";
    private static final String HIDDEN_FROM_PICKER_IME_LABEL = "Hidden From Picker IME";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private Instrumentation mInstrumentation;
    private Context mContext;
    private InputMethodManager mImManager;
    private boolean mNeedsImeReset = false;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mImManager = mContext.getSystemService(InputMethodManager.class);
    }

    @After
    public void resetImes() {
        if (mNeedsImeReset) {
            runShellCommandOrThrow("ime reset");
            mNeedsImeReset = false;
        }
    }

    /**
     * Verifies that the test API {@link InputMethodManager#isInputMethodPickerShown()} is properly
     * protected with some permission.
     *
     * <p>This is a regression test for Bug 237317525.</p>
     */
    @SecurityTest(minPatchLevel = "unknown")
    @Test
    public void testIsInputMethodPickerShownProtection() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS));
        assertThrows("InputMethodManager#isInputMethodPickerShown() must not be accessible to "
                + "normal apps.", SecurityException.class, mImManager::isInputMethodPickerShown);
    }

    /**
     * Verifies that the test API {@link InputMethodManager#addVirtualStylusIdForTestSession()} is
     * properly protected with some permission.
     */
    @Test
    public void testAddVirtualStylusIdForTestSessionProtection() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS));
        assertThrows("InputMethodManager#addVirtualStylusIdForTestSession() must not be accessible "
                + "to normal apps.", SecurityException.class,
                mImManager::addVirtualStylusIdForTestSession);
    }

    /**
     * Verifies that the test API {@link InputMethodManager#setStylusWindowIdleTimeoutForTest(long)}
     * is properly protected with some permission.
     */
    @Test
    public void testSetStylusWindowIdleTimeoutForTestProtection() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS));

        assertThrows("InputMethodManager#setStylusWindowIdleTimeoutForTest(long) must not"
                        + " be accessible to normal apps.", SecurityException.class,
                () -> mImManager.setStylusWindowIdleTimeoutForTest(0));
    }

    @Test
    public void testIsActive() throws Throwable {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            layout.addView(focusedEditText);
            focusedEditTextRef.set(focusedEditText);
            focusedEditText.requestFocus();

            final EditText nonFocusedEditText = new EditText(activity);
            layout.addView(nonFocusedEditText);
            nonFocusedEditTextRef.set(nonFocusedEditText);

            return layout;
        });
        final View focusedEditText = focusedEditTextRef.get();
        waitOnMainUntil(() -> mImManager.hasActiveInputConnection(focusedEditText), TIMEOUT);
        assertTrue(getOnMainSync(() -> mImManager.isActive(focusedEditText)));
        assertFalse(getOnMainSync(() -> mImManager.isActive(nonFocusedEditTextRef.get())));
    }

    @Test
    public void testIsAcceptingText() throws Throwable {
        final AtomicReference<EditText> focusedFakeEditTextRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedFakeEditText = new EditText(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo info) {
                    super.onCreateInputConnection(info);
                    latch.countDown();
                    return null;
                }
            };
            layout.addView(focusedFakeEditText);
            focusedFakeEditTextRef.set(focusedFakeEditText);
            focusedFakeEditText.requestFocus();
            return layout;
        });
        assertTrue(latch.await(TIMEOUT, TimeUnit.MICROSECONDS));
        assertFalse("InputMethodManager#isAcceptingText() must return false "
                + "if target View returns null from onCreateInputConnection().",
                getOnMainSync(() -> mImManager.isAcceptingText()));
    }

    @Test
    public void testGetInputMethodList() throws Exception {
        final List<InputMethodInfo> enabledImes = mImManager.getEnabledInputMethodList();
        assertNotNull(enabledImes);
        final List<InputMethodInfo> imes = mImManager.getInputMethodList();
        assertNotNull(imes);

        // Make sure that IMM#getEnabledInputMethodList() is a subset of IMM#getInputMethodList().
        // TODO: Consider moving this to hostside test to test more realistic and useful scenario.
        if (!imes.containsAll(enabledImes)) {
            fail("Enabled IMEs must be a subset of all the IMEs.\n"
                    + "all=" + dumpInputMethodInfoList(imes) + "\n"
                    + "enabled=" + dumpInputMethodInfoList(enabledImes));
        }
    }

    @Test
    public void testGetEnabledInputMethodList() throws Exception {
        enableImes(HIDDEN_FROM_PICKER_IME_ID);
        final List<InputMethodInfo> enabledImes = mImManager.getEnabledInputMethodList();
        assertThat(enabledImes).isNotNull();
        final List<String> enabledImeIds =
                enabledImes.stream().map(InputMethodInfo::getId).collect(Collectors.toList());
        assertThat(enabledImeIds).contains(HIDDEN_FROM_PICKER_IME_ID);
    }

    private static String dumpInputMethodInfoList(@NonNull List<InputMethodInfo> imiList) {
        return "[" + imiList.stream().map(imi -> {
            final StringBuilder sb = new StringBuilder();
            final int subtypeCount = imi.getSubtypeCount();
            sb.append("InputMethodInfo{id=").append(imi.getId())
                    .append(", subtypeCount=").append(subtypeCount)
                    .append(", subtypes=[");
            for (int i = 0; i < subtypeCount; ++i) {
                if (i != 0) {
                    sb.append(",");
                }
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                sb.append("{id=0x").append(Integer.toHexString(subtype.hashCode()));
                if (!TextUtils.isEmpty(subtype.getMode())) {
                    sb.append(",mode=").append(subtype.getMode());
                }
                if (!TextUtils.isEmpty(subtype.getLocale())) {
                    sb.append(",locale=").append(subtype.getLocale());
                }
                if (!TextUtils.isEmpty(subtype.getLanguageTag())) {
                    sb.append(",languageTag=").append(subtype.getLanguageTag());
                }
                sb.append("}");
            }
            sb.append("]");
            return sb.toString();
        }).collect(Collectors.joining(", ")) + "]";
    }

    @AppModeFull(reason = "Instant apps cannot rely on ACTION_CLOSE_SYSTEM_DIALOGS")
    @Test
    public void testShowInputMethodPicker() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS));
        enableImes(MOCK_IME_ID, HIDDEN_FROM_PICKER_IME_ID);

        TestActivity.startSync(activity -> {
            final View view = new View(activity);
            view.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return view;
        });

        // Make sure that InputMethodPicker is not shown in the initial state.
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !isInputMethodPickerShown(mImManager), TIMEOUT,
                "InputMethod picker should be closed");

        // Test InputMethodManager#showInputMethodPicker() works as expected.
        mImManager.showInputMethodPicker();
        waitOnMainUntil(() -> isInputMethodPickerShown(mImManager), TIMEOUT,
                "InputMethod picker should be shown");

        // UiDevice.getInstance(Instrumentation) may return a cached instance if it's already called
        // in this process and for some unknown reasons it fails to detect MOCK_IME_LABEL.
        // As a quick workaround, here we clear its internal singleton value.
        // TODO(b/230698095): Fix this in UiDevice or stop using UiDevice.
        try {
            final Field field = UiDevice.class.getDeclaredField("sInstance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException e) {
            // We don't treat this as an error as it's an implementation detail of UiDevice.
        }

        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        assertThat(uiDevice.wait(Until.hasObject(By.text(MOCK_IME_LABEL)), TIMEOUT)).isTrue();
        assertThat(uiDevice.findObject(By.text(HIDDEN_FROM_PICKER_IME_LABEL))).isNull();

        // Make sure that InputMethodPicker can be closed with ACTION_CLOSE_SYSTEM_DIALOGS
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !isInputMethodPickerShown(mImManager), TIMEOUT,
                "InputMethod picker should be closed");
    }

    @Test
    public void testNoStrongServedViewReferenceAfterWindowDetached() throws IOException {
        var receivedSignalCleaned = new CountDownLatch(1);
        Runnable r = () -> {
            var viewRef = new View[1];
            TestActivity testActivity = TestActivity.startSync(activity -> {
                viewRef[0] = new EditText(activity);
                viewRef[0].setLayoutParams(new LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                viewRef[0].requestFocus();
                return viewRef[0];
            });
            // wait until editText becomes active
            final InputMethodManager imm = testActivity.getSystemService(InputMethodManager.class);
            PollingCheck.waitFor(() -> imm.hasActiveInputConnection(viewRef[0]));

            Cleaner.create().register(viewRef[0], receivedSignalCleaned::countDown);
            viewRef[0] = null;

            // finishing the activity should destroy the reference inside IMM
            testActivity.finish();
        };
        r.run();

        waitForWithGc(() -> receivedSignalCleaned.getCount() == 0);
    }

    private void waitForWithGc(PollingCheck.PollingCheckCondition condition) throws IOException {
        try {
            PollingCheck.waitFor(() -> {
                Runtime.getRuntime().gc();
                return condition.canProceed();
            });
        } catch (AssertionError e) {
            File heap = new File("/sdcard/DumpOnFailure", "inputmethod-dump.hprof");
            Debug.dumpHprofData(heap.getAbsolutePath());
            throw new AssertionError("Dumped heap in device at " + heap.getAbsolutePath(), e);
        }
    }

    private void enableImes(String... ids) {
        for (String id : ids) {
            runShellCommandOrThrow("ime enable " + id);
        }
        mNeedsImeReset = true;
    }
}
