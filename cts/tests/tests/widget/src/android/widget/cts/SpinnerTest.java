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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.cts.util.TestUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Spinner}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SpinnerTest {
    private Instrumentation mInstrumentation;
    private CtsTouchUtils mCtsTouchUtils;
    private UiAutomation mUiAutomation;
    private Activity mActivity;
    private Spinner mSpinnerDialogMode;
    private Spinner mSpinnerDropdownMode;
    private static final int SPINNER_HAS_FOCUS_DELAY_MS = 500;
    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    @Rule
    public ActivityTestRule<SpinnerCtsActivity> mActivityRule =
            new ActivityTestRule<>(SpinnerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mCtsTouchUtils = new CtsTouchUtils(mInstrumentation.getTargetContext());
        mUiAutomation = mInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = mUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        mUiAutomation.setServiceInfo(info);
        mActivity = mActivityRule.getActivity();
        mSpinnerDialogMode = (Spinner) mActivity.findViewById(R.id.spinner_dialog_mode);
        mSpinnerDropdownMode = (Spinner) mActivity.findViewById(R.id.spinner_dropdown_mode);
    }

    @Test
    public void testConstructor() {
        new Spinner(mActivity);

        new Spinner(mActivity, null);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle);

        new Spinner(mActivity, Spinner.MODE_DIALOG);

        new Spinner(mActivity, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner_Underlined,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Spinner_Underlined,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner,
                Spinner.MODE_DROPDOWN);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner_Underlined,
                Spinner.MODE_DIALOG);

        new Spinner(mActivity, null, 0, android.R.style.Widget_Material_Light_Spinner_Underlined,
                Spinner.MODE_DROPDOWN);

        final Resources.Theme popupTheme = mActivity.getResources().newTheme();
        popupTheme.applyStyle(android.R.style.Theme_Material, true);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG,
                popupTheme);

        new Spinner(mActivity, null, android.R.attr.spinnerStyle, 0, Spinner.MODE_DROPDOWN,
                popupTheme);
    }

    private ArrayAdapter<CharSequence> getTestAdapter() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mActivity,
                R.array.string, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void verifyGetBaseline(Spinner spinner) throws Throwable {
        assertEquals(-1, spinner.getBaseline());

        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> {
            spinner.setAdapter(adapter);
            assertTrue(spinner.getBaseline() > 0);
        });
    }

    @Test
    public void testGetBaseline() throws Throwable {
        verifyGetBaseline(mSpinnerDialogMode);
        verifyGetBaseline(mSpinnerDropdownMode);
    }

    private void verifySetOnItemClickListener(Spinner spinner) {
        try {
            spinner.setOnItemClickListener(null);
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }

        try {
            spinner.setOnItemClickListener(mock(Spinner.OnItemClickListener.class));
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
        }
    }

    @Test
    public void testSetOnItemClickListener() {
        verifySetOnItemClickListener(mSpinnerDialogMode);
        verifySetOnItemClickListener(mSpinnerDropdownMode);
    }

    private void verifyPerformClick(Spinner spinner) throws Throwable {
        mActivityRule.runOnUiThread(() -> assertTrue(spinner.performClick()));
    }

    @Test
    public void testPerformClick() throws Throwable {
        verifyPerformClick(mSpinnerDialogMode);
        verifyPerformClick(mSpinnerDropdownMode);
    }

    private void verifyOnClick(Spinner spinner) {
        // normal value
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        AlertDialog alertDialog = builder.show();
        assertTrue(alertDialog.isShowing());

        spinner.onClick(alertDialog, 10);
        assertEquals(10, spinner.getSelectedItemPosition());
        assertFalse(alertDialog.isShowing());

        // exceptional
        try {
            spinner.onClick(null, 10);
            fail("did not throw NullPointerException");
        } catch (NullPointerException e) {
        }

        Dialog dialog = new Dialog(mActivity);
        dialog.show();
        assertTrue(dialog.isShowing());

        spinner.onClick(dialog, -10);
        assertEquals(-10, spinner.getSelectedItemPosition());
        assertFalse(dialog.isShowing());
    }

    private void verifyOnClickAccessibility(Spinner spinner) throws Exception {
        ArrayAdapter<CharSequence> adapter = getTestAdapter();

        // Initialization. Expecting a TYPE_VIEW_SELECTED event when setting selection.
        mInstrumentation.getUiAutomation().executeAndWaitForEvent(
                () -> mActivity.runOnUiThread(()-> {
                    spinner.setAdapter(adapter);
                    spinner.setSelection(2);
                }),
                event -> event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED,
                DEFAULT_TIMEOUT_MILLIS
        );

        // Clicking to expand popup. Expecting a window to be added.
        mInstrumentation.getUiAutomation().executeAndWaitForEvent(
                () -> mActivity.runOnUiThread(() -> spinner.performClick()),
                event -> event.getWindowChanges() == AccessibilityEvent.WINDOWS_CHANGE_ADDED,
                DEFAULT_TIMEOUT_MILLIS
        );

        // Simulating an item selection.
        // This should close the popup and then send TYPE_VIEW_SELECTED.
        mInstrumentation.getUiAutomation().executeAndWaitForEvent(
                () -> mActivity.runOnUiThread(() -> spinner.onClick(1)),
                event -> event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED,
                DEFAULT_TIMEOUT_MILLIS
        );
    }

    @UiThreadTest
    @Test
    public void testOnClick() {
        verifyOnClick(mSpinnerDialogMode);
        verifyOnClick(mSpinnerDropdownMode);
    }

    @Test
    public void testOnClickAccessibility() throws Throwable {
        AccessibilityManager accessibilityManager = AccessibilityManager.getInstance(mActivity);
        PollingCheck.waitFor(DEFAULT_TIMEOUT_MILLIS, () -> accessibilityManager.isEnabled());
        verifyOnClickAccessibility(mSpinnerDropdownMode);
        verifyOnClickAccessibility(mSpinnerDialogMode);
    }

    private void verifyAccessPrompt(Spinner spinner) throws Throwable {
        final String initialPrompt = mActivity.getString(R.string.text_view_hello);
        assertEquals(initialPrompt, spinner.getPrompt());

        final String promptText = "prompt text";

        mActivityRule.runOnUiThread(() -> spinner.setPrompt(promptText));
        assertEquals(promptText, spinner.getPrompt());

        spinner.setPrompt(null);
        assertNull(spinner.getPrompt());
    }

    @Test
    public void testAccessPrompt() throws Throwable {
        verifyAccessPrompt(mSpinnerDialogMode);
        verifyAccessPrompt(mSpinnerDropdownMode);
    }

    private void verifySetPromptId(Spinner spinner) throws Throwable {
        mActivityRule.runOnUiThread(() -> spinner.setPromptId(R.string.hello_world));
        assertEquals(mActivity.getString(R.string.hello_world), spinner.getPrompt());

        try {
            spinner.setPromptId(-1);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }

        try {
            spinner.setPromptId(Integer.MAX_VALUE);
            fail("Should throw NotFoundException");
        } catch (NotFoundException e) {
            // issue 1695243, not clear what is supposed to happen if promptId is exceptional.
        }
    }

    @Test
    public void testSetPromptId() throws Throwable {
        verifySetPromptId(mSpinnerDialogMode);
        verifySetPromptId(mSpinnerDropdownMode);
    }

    @UiThreadTest
    @Test
    public void testGetPopupContext() {
        Theme theme = mActivity.getResources().newTheme();
        Spinner themeSpinner = new Spinner(mActivity, null,
                android.R.attr.spinnerStyle, 0, Spinner.MODE_DIALOG, theme);
        assertNotSame(mActivity, themeSpinner.getPopupContext());
        assertSame(theme, themeSpinner.getPopupContext().getTheme());

        ContextThemeWrapper context = (ContextThemeWrapper)themeSpinner.getPopupContext();
        assertSame(mActivity, context.getBaseContext());
    }

    private void verifyGravity(Spinner spinner) throws Throwable {
        // Note that here we're using a custom layout for the spinner's selected item
        // that doesn't span the whole width of the parent. That way we're exercising the
        // relevant path in spinner's layout pass that handles the currently set gravity
        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> spinner.setAdapter(adapter));

        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, spinner, () -> {
            spinner.setSelection(1);
            spinner.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            spinner.requestLayout();
        });

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.LEFT));
        assertEquals(Gravity.LEFT, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.CENTER_HORIZONTAL));
        assertEquals(Gravity.CENTER_HORIZONTAL, spinner.getGravity());

        mActivityRule.runOnUiThread((() -> spinner.setGravity(Gravity.RIGHT)));
        assertEquals(Gravity.RIGHT, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.START));
        assertEquals(Gravity.START, spinner.getGravity());

        mActivityRule.runOnUiThread(() -> spinner.setGravity(Gravity.END));
        assertEquals(Gravity.END, spinner.getGravity());
    }

    @Test
    public void testGravity() throws Throwable {
        verifyGravity(mSpinnerDialogMode);
        verifyGravity(mSpinnerDropdownMode);
    }

    @Test
    public void testDropDownMetricsDropdownMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> mSpinnerDropdownMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        mActivityRule.runOnUiThread(() -> {
            mSpinnerDropdownMode.setDropDownWidth(dropDownWidth);
            mSpinnerDropdownMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDropdownMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule,
                mSpinnerDropdownMode);
        // Verify that we're showing the popup
        PollingCheck.waitFor(() -> mSpinnerDropdownMode.isPopupShowing());

        // And test its attributes
        assertEquals(dropDownWidth, mSpinnerDropdownMode.getDropDownWidth());
        // TODO: restore when b/28089349 is addressed
        // assertEquals(dropDownOffsetHorizontal,
        //      mSpinnerDropdownMode.getDropDownHorizontalOffset());
        assertEquals(dropDownOffsetVertical, mSpinnerDropdownMode.getDropDownVerticalOffset());
    }

    @Test
    public void testDropDownMetricsDialogMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> mSpinnerDialogMode.setAdapter(adapter));

        final Resources res = mActivity.getResources();
        final int dropDownWidth = res.getDimensionPixelSize(R.dimen.spinner_dropdown_width);
        final int dropDownOffsetHorizontal =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_h);
        final int dropDownOffsetVertical =
                res.getDimensionPixelSize(R.dimen.spinner_dropdown_offset_v);

        mActivityRule.runOnUiThread(() -> {
            // These are all expected to be no-ops
            mSpinnerDialogMode.setDropDownWidth(dropDownWidth);
            mSpinnerDialogMode.setDropDownHorizontalOffset(dropDownOffsetHorizontal);
            mSpinnerDialogMode.setDropDownVerticalOffset(dropDownOffsetVertical);
        });

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mSpinnerDialogMode);
        // Verify that we're showing the popup
        PollingCheck.waitFor(() -> mSpinnerDialogMode.isPopupShowing());

        // And test its attributes. Note that we are not testing the result of getDropDownWidth
        // for this mode
        assertEquals(0, mSpinnerDialogMode.getDropDownHorizontalOffset());
        assertEquals(0, mSpinnerDialogMode.getDropDownVerticalOffset());
    }

    @Test
    public void testDropDownBackgroundDropdownMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> mSpinnerDropdownMode.setAdapter(adapter));

        // Set blue background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDropdownMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule,
                mSpinnerDropdownMode);
        // Verify that we're showing the popup
        PollingCheck.waitFor(() -> mSpinnerDropdownMode.isPopupShowing());
        // And test its fill
        Drawable dropDownBackground = mSpinnerDropdownMode.getPopupBackground();
        TestUtils.assertAllPixelsOfColor("Drop down should be blue", dropDownBackground,
                dropDownBackground.getBounds().width(), dropDownBackground.getBounds().height(),
                false, Color.BLUE, 1, true);
    }

    @Test
    public void testDropDownBackgroundDialogMode() throws Throwable {
        ArrayAdapter<CharSequence> adapter = getTestAdapter();
        mActivityRule.runOnUiThread(() -> mSpinnerDialogMode.setAdapter(adapter));

        // Set blue background on the popup
        mActivityRule.runOnUiThread(() ->
                mSpinnerDialogMode.setPopupBackgroundResource(R.drawable.blue_fill));

        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mSpinnerDialogMode);
        // Verify that we're showing the popup
        PollingCheck.waitFor(() -> mSpinnerDialogMode.isPopupShowing());
        // And test that getPopupBackground returns null
        assertNull(mSpinnerDialogMode.getPopupBackground());
    }
}
