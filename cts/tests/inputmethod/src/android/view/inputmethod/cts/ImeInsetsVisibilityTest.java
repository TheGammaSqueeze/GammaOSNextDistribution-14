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

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.isInputMethodPickerShown;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.showSoftInputMatcher;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ImeInsetsVisibilityTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    private static final int NEW_KEYBOARD_HEIGHT = 300;

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.ImeInsetsVisibilityTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    @Test
    public void testImeVisibilityWhenImeFocusableChildPopup() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus", getOnMainSync(
                    () -> editText.requestFocus() && imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            PollingCheck.check("Ime insets should be visible", TIMEOUT,
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);

            try (ChildWindowHolder childWindow = createChildTransparentApplicationWindowOnMain(
                    activity, 200 /* width */, 200 /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM,
                    WindowInsets.Type.ime() | WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars())) {
                // The window will be shown above (in y-axis) the IME.
                TestUtils.runOnMainSync(
                        () -> childWindow.getRootView().setVisibility(View.VISIBLE));
                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        TIMEOUT, "Ime insets should be visible");
                expectImeVisible(TIMEOUT);
            }
        }
    }

    @Test
    public void testImeVisibilityWhenImeFocusableGravityBottomChildPopup() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus", getOnMainSync(
                    () -> editText.requestFocus() && imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            PollingCheck.check("Ime insets should be visible", TIMEOUT,
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);

            try (ChildWindowHolder childWindow = createChildBottomPanelWindowOnMain(activity,
                    MATCH_PARENT /* width */, NEW_KEYBOARD_HEIGHT /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM)) {
                // The window will be shown above (in y-axis) the IME.
                TestUtils.runOnMainSync(() -> {
                    childWindow.getRootView().setBackgroundColor(Color.RED);
                    childWindow.getRootView().setVisibility(View.VISIBLE);
                });
                // IME should be on screen without reset.
                notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        TIMEOUT, "Ime insets should be visible");
                expectImeVisible(TIMEOUT);
            }
        }
    }

    @Test
    public void testImeVisibilityWhenImeFocusableChildPopupOverlaps() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(false, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus", getOnMainSync(
                    () -> editText.requestFocus() && imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            PollingCheck.check("Ime insets should be visible", TIMEOUT,
                    () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            expectImeVisible(TIMEOUT);

            try (ChildWindowHolder childWindow = createChildBottomPanelWindowOnMain(activity,
                    MATCH_PARENT /* width */, NEW_KEYBOARD_HEIGHT /* height */,
                    FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM | FLAG_LAYOUT_IN_SCREEN)) {
                // The window will be shown behind (in z-axis) the IME.
                TestUtils.runOnMainSync(() -> {
                    childWindow.getRootView().setBackgroundColor(Color.RED);
                    childWindow.getRootView().setVisibility(View.VISIBLE);
                });
                // IME should be on screen without reset.
                notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

                TestUtils.waitOnMainUntil(
                        () -> editText.getRootWindowInsets().isVisible(WindowInsets.Type.ime()),
                        TIMEOUT, "Ime insets should be visible");
                expectImeVisible(TIMEOUT);
            }
        }
    }

    @AppModeFull(reason = "Instant apps cannot rely on ACTION_CLOSE_SYSTEM_DIALOGS")
    @Test
    public void testEditTextPositionAndPersistWhenAboveImeWindowShown() throws Exception {
        Assume.assumeFalse(isPreventImeStartup());
        final InputMethodManager imm = getImmOrFail();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setInputViewHeight(NEW_KEYBOARD_HEIGHT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final Pair<EditText, TestActivity> editTextTestActivityPair =
                    launchTestActivity(true, marker);
            final EditText editText = editTextTestActivityPair.first;
            final TestActivity activity = editTextTestActivityPair.second;
            final WindowInsets[] insetsFromActivity = new WindowInsets[1];
            Point curEditPos = getLocationOnScreenForView(editText);

            TestUtils.runOnMainSync(() -> {
                activity.getWindow().getDecorView().setOnApplyWindowInsetsListener(
                        (v, insets) -> insetsFromActivity[0] = insets);
            });

            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("showSoftInput must success if the View has IME focus", getOnMainSync(
                    () -> editText.requestFocus() && imm.showSoftInput(editText, 0)));

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            Point lastEditTextPos = new Point(curEditPos);
            curEditPos = getLocationOnScreenForView(editText);
            // Watch doesn't support navigation bar and has limited screen size, so no transition
            // in editbox with respect to x and y coordinates
            Configuration config = InstrumentationRegistry.getInstrumentation()
                    .getContext()
                    .getResources()
                    .getConfiguration();
            boolean isSmallScreenLayout =
                    config.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_SMALL);

            if (isSmallScreenLayout) {
                assertTrue("Insets should visible",
                        isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime()));
            } else {
                assertTrue("Insets should visible and EditText position should be adjusted",
                        isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime())
                                && curEditPos.y < lastEditTextPos.y);
            }

            imm.showInputMethodPicker();
            TestUtils.waitOnMainUntil(() -> isInputMethodPickerShown(imm) && editText.isLaidOut(),
                    TIMEOUT, "InputMethod picker should be shown");
            lastEditTextPos = new Point(curEditPos);
            curEditPos = getLocationOnScreenForView(editText);

            assertTrue("Insets visibility & EditText position should persist when "
                            + "the above IME window shown",
                    isInsetsVisible(insetsFromActivity[0], WindowInsets.Type.ime())
                            && curEditPos.equals(lastEditTextPos));

            InstrumentationRegistry.getInstrumentation().getContext().sendBroadcast(
                    new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
            TestUtils.waitOnMainUntil(() -> !isInputMethodPickerShown(imm), TIMEOUT,
                    "InputMethod picker should be closed");
        }
    }

    /**
     * Test the IME window won't cover the editor when the app creates a panel window to receive
     * the IME insets.
     *
     * <p>Regression test for Bug 195765264 and Bug 152304051.</p>
     */
    @Test
    public void testEditorWontCoveredByImeWhenInputWindowBehindPanel() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            // Launch a test activity with SOFT_INPUT_ADJUST_NOTHING to not resize by IME insets.
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.BOTTOM);
                final EditText editText = new EditText(activity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                // Initial editor visibility as GONE for testing IME visibility controlled by panel.
                editText.setVisibility(View.GONE);
                editTextRef.set(editText);
                layout.addView(editText);
                activity.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                return layout;
            });
            final EditText editText = editTextRef.get();
            // Create a panel window to receive IME insets for adjusting editText position.
            final View panelView = TestUtils.getOnMainSync(() -> {
                final View panel = new View(testActivity);
                panel.setOnApplyWindowInsetsListener((v, insets) -> {
                    if (insets.isVisible(WindowInsets.Type.ime())) {
                        // Request editText focused when IME insets visible.
                        editText.setVisibility(View.VISIBLE);
                        editText.requestFocus();
                        LinearLayout.LayoutParams lp =
                                (LinearLayout.LayoutParams) editText.getLayoutParams();
                        lp.setMargins(0, 0, 0, editText.getRootView().getMeasuredHeight()
                                - panel.getMeasuredHeight());
                        editText.requestLayout();
                    } else {
                        // Clear editText focused when IME insets invisible.
                        editText.clearFocus();
                        editText.setVisibility(View.GONE);
                    }
                    return insets;
                });
                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        1, MATCH_PARENT,
                        0, 0, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                        PixelFormat.TRANSLUCENT);
                lp.setFitInsetsTypes(WindowInsets.Type.ime() | WindowInsets.Type.systemBars());
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                lp.token = testActivity.getWindow().getDecorView().getWindowToken();
                testActivity.getWindowManager().addView(panel, lp);
                return panel;
            });
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);
            // Show IME by using WindowInsets API.
            testActivity.getWindow().getInsetsController().show(WindowInsets.Type.ime());
            TestUtils.waitOnMainUntil(() -> isInsetsVisible(panelView.getRootWindowInsets(),
                    WindowInsets.Type.ime()), TIMEOUT, "The panel should receive IME insets");
            TestUtils.waitOnMainUntil(
                    () -> editText.getVisibility() == View.VISIBLE && editText.hasFocus(),
                    TIMEOUT, "The editor should be shown and visible");
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    private boolean isInsetsVisible(WindowInsets winInsets, int type) {
        if (winInsets == null) {
            return false;
        }
        return winInsets.isVisible(type);
    }

    private Point getLocationOnScreenForView(View view) {
        return TestUtils.getOnMainSync(() -> {
            final int[] tmpPos = new int[2];
            view.getLocationOnScreen(tmpPos);
            return new Point(tmpPos[0], tmpPos[1]);
        });
    }

    private Pair<EditText, TestActivity> launchTestActivity(boolean useDialogTheme,
            @NonNull String focusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<TestActivity> testActivityRef = new AtomicReference<>();

        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.BOTTOM);
            if (useDialogTheme) {
                // Create a floating Dialog
                activity.setTheme(android.R.style.Theme_Material_Dialog);
                TextView textView = new TextView(activity);
                textView.setText("I'm a TextView");
                textView.setHeight(activity.getWindowManager().getMaximumWindowMetrics()
                        .getBounds().height() / 3);
                layout.addView(textView);
            }

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);

            focusedEditTextRef.set(focusedEditText);
            testActivityRef.set(activity);

            layout.addView(focusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), testActivityRef.get());
    }

    /**
     * A utility class to pack the root {@link View} and its clean-up operation that is compatible
     * with {@link AutoCloseable} protocol.
     */
    private static final class ChildWindowHolder implements AutoCloseable {
        @NonNull
        private final View mRootView;

        private ChildWindowHolder(@NonNull View rootView) {
            mRootView = rootView;
        }

        @NonNull
        @AnyThread
        View getRootView() {
            return mRootView;
        }

        @Override
        public void close() {
            TestUtils.runOnMainSync(() -> mRootView.getContext()
                    .getSystemService(WindowManager.class).removeView(mRootView));
        }
    }

    @NonNull
    private ChildWindowHolder createChildBottomPanelWindowOnMain(Activity activity, int width,
            int height, int windowFlags) {
        return TestUtils.getOnMainSync(() -> {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.token = null;
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            attrs.width = width;
            attrs.height = height;
            attrs.gravity = Gravity.BOTTOM;
            attrs.flags = windowFlags;
            final View childViewRoot = new View(activity);
            activity.getSystemService(WindowManager.class).addView(childViewRoot, attrs);
            return new ChildWindowHolder(childViewRoot);
        });
    }

    @NonNull
    private ChildWindowHolder createChildTransparentApplicationWindowOnMain(Activity activity,
            int width, int height, int windowFlags, int fitInsetsTypes) {
        return TestUtils.getOnMainSync(() -> {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.token = activity.getWindow().getAttributes().token;
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            attrs.width = width;
            attrs.height = height;
            attrs.format = PixelFormat.TRANSPARENT;
            attrs.gravity = Gravity.NO_GRAVITY;
            attrs.flags = windowFlags;
            attrs.setFitInsetsTypes(fitInsetsTypes);
            final View childViewRoot = new View(activity);
            activity.getSystemService(WindowManager.class).addView(childViewRoot, attrs);
            return new ChildWindowHolder(childViewRoot);
        });
    }

    @NonNull
    private static InputMethodManager getImmOrFail() {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        assertNotNull(imm);
        return imm;
    }
}
