/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.view.flags.Flags.FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.PointerIcon;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointerIconTest {

    @Rule
    public final ActivityTestRule<PointerIconCtsActivity> mActivityRule =
            new ActivityTestRule<>(PointerIconCtsActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Activity mActivity;
    private View mTopView;
    private PointerIcon mArrowIcon;
    private PointerIcon mHandIcon;
    private PointerIcon mHelpIcon;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTopView = mActivity.findViewById(R.id.top);
        mArrowIcon = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_ARROW);
        mHandIcon = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HAND);
        mHelpIcon = PointerIcon.getSystemIcon(mActivity, PointerIcon.TYPE_HELP);
    }

    private void assertMousePointerIcon(String message, PointerIcon expectedIcon, View target) {
        assertPointerIcon(message, expectedIcon, target, InputDevice.SOURCE_MOUSE,
                MotionEvent.TOOL_TYPE_MOUSE);
    }

    private void assertStylusPointerIcon(String message, PointerIcon expectedIcon, View target) {
        assertPointerIcon(message, expectedIcon, target, InputDevice.SOURCE_STYLUS,
                MotionEvent.TOOL_TYPE_STYLUS);
    }

    private void assertPointerIcon(String message, PointerIcon expectedIcon, View target,
            int source, @MotionEvent.ToolType int toolType) {
        final int[] topPos = new int[2];
        mTopView.getLocationOnScreen(topPos);
        final int[] targetPos = new int[2];
        target.getLocationOnScreen(targetPos);
        final int x = targetPos[0] + target.getWidth() / 2 - topPos[0];
        final int y = targetPos[1] + target.getHeight() / 2 - topPos[1];

        final PointerCoords[] pcs = new PointerCoords[1];
        pcs[0] = new PointerCoords();
        pcs[0].setAxisValue(MotionEvent.AXIS_X, x);
        pcs[0].setAxisValue(MotionEvent.AXIS_Y, y);

        final MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[1];
        pps[0] = new MotionEvent.PointerProperties();
        pps[0].toolType = toolType;

        final MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 1, pps,
                pcs, 0, 0, x, y, 0, 0, source, 0);

        assertEquals(message, expectedIcon, mTopView.onResolvePointerIcon(event, 0));
    }

    // Checks for previous icons prior to Android V (and some versions U).
    private void assertDefaultWidgetMousePointerIconBehaviorWithoutFlag(View view) {
        assertMousePointerIcon("Default pointer icon", mHandIcon, view);

        view.setEnabled(false);
        assertMousePointerIcon("Disabled view has no pointer icon", null, view);

        view.setEnabled(true);
        assertMousePointerIcon("Enabled view has default pointer icon", mHandIcon, view);

        view.setPointerIcon(mHelpIcon);
        assertMousePointerIcon("Override pointer icon", mHelpIcon, view);

        view.setPointerIcon(null);
        assertMousePointerIcon("Revert to default pointer icon", mHandIcon, view);
    }

    /*
     * The Widgets (though based on Button) do not use null as the default to represent the arrow.
     * Instead, they use the arrow icon itself.
     */
    private void assertDefaultWidgetMousePointerIconBehavior(View view) {
        assertMousePointerIcon("Default pointer icon", mArrowIcon, view);

        view.setEnabled(false);
        assertMousePointerIcon("Disabled view has no pointer icon", null, view);

        view.setEnabled(true);
        assertMousePointerIcon("Enabled view has default pointer icon", mArrowIcon, view);

        view.setPointerIcon(mHelpIcon);
        assertMousePointerIcon("Override pointer icon", mHelpIcon, view);

        view.setPointerIcon(null);
        assertMousePointerIcon("Revert to default pointer icon", mArrowIcon, view);
    }

    /*
     * By default, Button uses null to represent the arrow and when it changes back to the default
     * from another icon (for example, from "help"), it will again use null (which represents the
     * Arrow).
     */
    private void assertDefaultWidgetAsNullMousePointerIconBehavior(View view) {
        assertMousePointerIcon("Default pointer icon", null, view);

        view.setEnabled(false);
        assertMousePointerIcon("Disabled view has no pointer icon", null, view);

        view.setEnabled(true);
        assertMousePointerIcon("Enabled view has default pointer icon", null, view);

        view.setPointerIcon(mHelpIcon);
        assertMousePointerIcon("Override pointer icon", mHelpIcon, view);

        view.setPointerIcon(null);
        assertMousePointerIcon("Revert to default pointer icon", null, view);
    }

    private TabHost.TabSpec createTabSpec(TabHost tabHost, String label, PointerIcon pointerIcon) {
        final TextView tabIndicator = new TextView(mActivity);
        tabIndicator.setText(label);
        tabIndicator.setPointerIcon(pointerIcon);
        return tabHost.newTabSpec(label)
                .setIndicator(tabIndicator)
                .setContent(tag -> new View(mActivity));
    }

    @UiThreadTest
    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testButtonWithoutFlag() {
        assertDefaultWidgetMousePointerIconBehaviorWithoutFlag(mActivity.findViewById(R.id.button));
    }

    @UiThreadTest
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testButton() {
        assertDefaultWidgetAsNullMousePointerIconBehavior(mActivity.findViewById(R.id.button));
    }

    @UiThreadTest
    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testImageButtonWithoutFlag() {
        assertDefaultWidgetMousePointerIconBehaviorWithoutFlag(
                mActivity.findViewById(R.id.image_button)
        );
    }

    @UiThreadTest
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testImageButton() {
        assertDefaultWidgetMousePointerIconBehavior(mActivity.findViewById(R.id.image_button));
    }

    @UiThreadTest
    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testSpinnerButtonWithoutFlag() {
        assertDefaultWidgetMousePointerIconBehaviorWithoutFlag(
                mActivity.findViewById(R.id.spinner)
        );
    }

    @UiThreadTest
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testSpinnerButton() {
        assertDefaultWidgetMousePointerIconBehavior(mActivity.findViewById(R.id.spinner));
    }

    @FlakyTest(bugId = 124009655)
    @Test
    @RequiresFlagsDisabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testTabWidgetWithoutFlag() throws Throwable {
        final TabHost tabHost = (TabHost) mActivity.findViewById(android.R.id.tabhost);

        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                () -> {
                    tabHost.setup();
                    tabHost.addTab(createTabSpec(tabHost, "Tab 0", null));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 1", mHandIcon));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 2", mHelpIcon));
                },
                false /* force layout */);

        mActivityRule.runOnUiThread(() -> {
            final TabWidget tabWidget = tabHost.getTabWidget();

            tabWidget.setEnabled(false);
            assertMousePointerIcon("Disabled Tab 0", null, tabWidget.getChildTabViewAt(0));
            assertMousePointerIcon("Disabled Tab 1", null, tabWidget.getChildTabViewAt(1));
            assertMousePointerIcon("Disabled Tab 2", null, tabWidget.getChildTabViewAt(2));

            tabWidget.setEnabled(true);
            assertMousePointerIcon("Tab 0", mHandIcon, tabWidget.getChildTabViewAt(0));
            assertMousePointerIcon("Tab 1", mHandIcon, tabWidget.getChildTabViewAt(1));
            assertMousePointerIcon("Tab 2", mHelpIcon, tabWidget.getChildTabViewAt(2));
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ARROW_ICON_ON_HOVER_WHEN_CLICKABLE)
    public void testTabWidget() throws Throwable {
        final TabHost tabHost = (TabHost) mActivity.findViewById(android.R.id.tabhost);

        WidgetTestUtils.runOnMainAndLayoutSync(
                mActivityRule,
                () -> {
                    tabHost.setup();
                    tabHost.addTab(createTabSpec(tabHost, "Tab 0", null));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 1", mHandIcon));
                    tabHost.addTab(createTabSpec(tabHost, "Tab 2", mHelpIcon));
                },
                false /* force layout */);

        mActivityRule.runOnUiThread(() -> {
            final TabWidget tabWidget = tabHost.getTabWidget();

            tabWidget.setEnabled(false);
            assertMousePointerIcon("Disabled Tab 0", null, tabWidget.getChildTabViewAt(0));
            assertMousePointerIcon("Disabled Tab 1", null, tabWidget.getChildTabViewAt(1));
            assertMousePointerIcon("Disabled Tab 2", null, tabWidget.getChildTabViewAt(2));

            tabWidget.setEnabled(true);
            assertMousePointerIcon("Tab 0", null, tabWidget.getChildTabViewAt(0));
            assertMousePointerIcon("Tab 1", mHandIcon, tabWidget.getChildTabViewAt(1));
            assertMousePointerIcon("Tab 2", mHelpIcon, tabWidget.getChildTabViewAt(2));
        });
    }

    @UiThreadTest
    @Test
    public void testSetPointerIconIsNotUsedForStylusByDefault() {
        final View view = mActivity.findViewById(R.id.empty_view);

        assertMousePointerIcon("Default mouse pointer icon", null, view);
        assertStylusPointerIcon("Default stylus pointer icon", null, view);

        // setPointerIcon() should only affect the pointer icon for mouse devices by default.
        view.setPointerIcon(mHandIcon);
        assertMousePointerIcon("setPointerIcon applies to mouse pointers", mHandIcon, view);
        assertStylusPointerIcon("setPointerIcon does not apply to stylus pointers", null, view);
    }
}
