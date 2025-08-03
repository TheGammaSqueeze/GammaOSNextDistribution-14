/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.traceur.uitest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class TraceurAppTests {

    private static final String TRACEUR_PACKAGE = "com.android.traceur";
    private static final String RECYCLERVIEW_ID = "com.android.traceur:id/recycler_view";
    private static final int LAUNCH_TIMEOUT_MS = 10000;
    private static final int UI_TIMEOUT_MS = 7500;
    private static final int SHORT_PAUSE_MS = 1000;
    private static final int MAX_SCROLL_SWIPES = 10;

    private UiDevice mDevice;
    private UiScrollable mScrollableMainScreen;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }

            // Press Menu to skip the lock screen.
            // In case we weren't on the lock screen, press Home to return to a clean launcher.
            mDevice.pressMenu();
            mDevice.pressHome();

            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to freeze device orientation.", e);
        }

        mDevice.waitForIdle();

        Context context = InstrumentationRegistry.getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(TRACEUR_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);

        // Wait for the app to appear.
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(TRACEUR_PACKAGE).depth(0)),
                  LAUNCH_TIMEOUT_MS));

        // The ID for the scrollable RecyclerView is used to find the specific view that we want,
        // because scrollable views may exist higher in the view hierarchy.
        mScrollableMainScreen =
                new UiScrollable(new UiSelector().scrollable(true).resourceId(RECYCLERVIEW_ID));
        if (mScrollableMainScreen.exists()) {
            mScrollableMainScreen.setAsVerticalList();
            mScrollableMainScreen.setMaxSearchSwipes(MAX_SCROLL_SWIPES);
        }

        // Default trace categories are restored in case a previous test modified them and
        // terminated early.
        restoreDefaultCategories();

        // Ensure that the test begins at the top of the main screen.
        returnToTopOfMainScreen();
    }

    @After
    public void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        // Finish Traceur activity.
        mDevice.pressBack();
        mDevice.pressHome();
    }

    /**
     * Verifies that the main page contains the correct UI elements.
     * If the main page is scrollable, the test checks that all expected elements are found while
     * scrolling. Otherwise, it checks that the expected elements are already on the page.
     */
    @Presubmit
    @Test
    public void testElementsOnMainScreen() throws Exception {
        String[] elementTitles = {
            "Record trace",
            "Record CPU profile",
            "Collect Winscope traces",
            "Trace debuggable applications",
            "Categories",
            "Restore default categories",
            "Per-CPU buffer size",
            "Long traces",
            "Maximum long trace size",
            "Maximum long trace duration",
            "View saved files",
            "Clear saved files",
            // This is intentionally disabled because it can differ between internal and AOSP.
            // "Stop recording for bug reports",
            "Show Quick Settings tile",
        };
        for (String title : elementTitles) {
            assertNotNull(title + " element not found.", findObjectOnMainScreenByText(title));
        }
    }

    /**
     * Checks that a trace can be recorded and shared.
     * This test records a trace by toggling 'Record trace' in the UI, taps on the share
     * notification once the trace is saved, then (on non-AOSP) verifies that a share dialog
     * appears.
     */
    @Presubmit
    @Test
    public void testSuccessfulTracing() throws Exception {
        UiObject2 recordTraceSwitch = findObjectOnMainScreenByText("Record trace");
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        mDevice.wait(Until.hasObject(By.text("Trace is being recorded")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.text("Trace is being recorded")), UI_TIMEOUT_MS);

        recordTraceSwitch = findObjectOnMainScreenByText("Record trace");
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        waitForShareHUN();
        tapShareNotification();
        clickThroughShareSteps();
    }

    /**
     * Checks that a trace with Winscope data can be recorded and shared.
     * This test enables Winscope data collection by toggling 'Collect Winscope traces' in the UI,
     * then records a trace by toggling 'Record trace', taps on the share notification once
     * the trace is saved, then (on non-AOSP) verifies that a share dialog appears.
     */
    @Presubmit
    @Test
    public void testSuccessfulWinscopeTracing() throws Exception {
        UiObject2 collectWinscopeTracesSwitch =
            findObjectOnMainScreenByText("Collect Winscope traces");
        assertNotNull("Collect Winscope traces switch not found.", collectWinscopeTracesSwitch);
        collectWinscopeTracesSwitch.click();

        UiObject2 recordTraceSwitch = findObjectOnMainScreenByText("Record trace");
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        mDevice.wait(Until.hasObject(By.text("Trace is being recorded")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.text("Trace is being recorded")), UI_TIMEOUT_MS);

        recordTraceSwitch = findObjectOnMainScreenByText("Record trace");
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        waitForShareHUN();
        tapShareNotification();
        clickThroughShareSteps();
    }

    /**
     * Checks that stack samples can be recorded and shared.
     * This test records stack samples by toggling 'Record CPU profile' in the UI, taps on the share
     * notification once the trace is saved, then (on non-AOSP) verifies that a share dialog
     * appears.
     */
    @Presubmit
    @Test
    public void testSuccessfulCpuProfiling() throws Exception {
        UiObject2 recordCpuProfileSwitch = findObjectOnMainScreenByText("Record CPU profile");
        assertNotNull("Record CPU profile switch not found.", recordCpuProfileSwitch);
        recordCpuProfileSwitch.click();

        mDevice.waitForIdle();

        // The full "Stack samples are being recorded" text may be cut off.
        mDevice.wait(Until.hasObject(By.textContains("Stack samples are")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.textContains("Stack samples are")), UI_TIMEOUT_MS);

        recordCpuProfileSwitch = findObjectOnMainScreenByText("Record CPU profile");
        assertNotNull("Record CPU profile switch not found.", recordCpuProfileSwitch);
        recordCpuProfileSwitch.click();

        mDevice.waitForIdle();

        waitForShareHUN();
        tapShareNotification();
        clickThroughShareSteps();
    }

    /**
     * Checks that trace categories are displayed after tapping on the 'Categories' button.
     */
    @Presubmit
    @Test
    public void testTraceCategoriesExist() throws Exception {
        openTraceCategories();
        List<UiObject2> categories = getTraceCategories();
        assertNotNull("List of categories not found.", categories);
        assertTrue("No available trace categories.", categories.size() > 0);
    }

    /**
     * Checks that the 'Categories' summary updates when trace categories are selected.
     * This test checks that the summary for the 'Categories' button changes from 'Default' to 'N
     * selected' when a trace category is clicked, then back to 'Default' when the same category is
     * clicked again.
     */
    @Presubmit
    @Test
    public void testCorrectCategoriesSummary() throws Exception {
        UiObject2 summary = getCategoriesSummary();
        assertTrue("Expected 'Default' summary not found on startup.",
                summary.getText().contains("Default"));

        openTraceCategories();
        toggleFirstTraceCategory();

        // The summary must be reset after each toggle because the reference will be stale.
        summary = getCategoriesSummary();
        assertTrue("Expected 'N selected' summary not found.",
                summary.getText().contains("selected"));

        openTraceCategories();
        toggleFirstTraceCategory();

        summary = getCategoriesSummary();
        assertTrue("Expected 'Default' summary not found after changing categories.",
                summary.getText().contains("Default"));
    }

    /**
     * Checks that the 'Restore default categories' button resets the trace categories summary.
     * This test changes the set of selected trace categories from the default, then checks that the
     * 'Categories' summary resets to 'Default' when the restore button is clicked.
     */
    @Presubmit
    @Test
    public void testRestoreDefaultCategories() throws Exception {
        openTraceCategories();
        toggleFirstTraceCategory();

        UiObject2 summary = getCategoriesSummary();
        assertTrue("Expected 'N selected' summary not found.",
                summary.getText().contains("selected"));

        restoreDefaultCategories();
        returnToTopOfMainScreen();

        // The summary must be reset after the toggle because the reference will be stale.
        summary = getCategoriesSummary();
        assertTrue("Expected 'Default' summary not found after restoring categories.",
                summary.getText().contains("Default"));
    }

    /**
     * Returns to the top of the main Traceur screen if it is scrollable.
     */
    private void returnToTopOfMainScreen() throws Exception {
        if (mScrollableMainScreen.exists()) {
            mScrollableMainScreen.setAsVerticalList();
            mScrollableMainScreen.scrollToBeginning(10);
        }
    }

    /**
     * Finds and returns the specified element by text, scrolling down if needed.
     * This method makes the assumption that Traceur's main screen is open, and shouldn't be used as
     * a general way to find UI elements elsewhere.
     */
    private UiObject2 findObjectOnMainScreenByText(String text)
            throws Exception {
        if (mScrollableMainScreen.exists()) {
            mScrollableMainScreen.scrollTextIntoView(text);
        }
        return mDevice.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS);
    }

    /**
     * This method waits for the share heads-up notification to appear and disappear.
     * This is intended to allow for the notification in the shade to be reliably clicked, and is
     * only used in testSuccessfulTracing and testSuccessfulCpuProfiling.
     */
    private void waitForShareHUN() throws Exception {
        mDevice.wait(Until.hasObject(By.text("Tap to share your recording")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.text("Tap to share your recording")), UI_TIMEOUT_MS);
    }

    /**
     * This method opens the notification shade and taps on the share notification.
     * This is only used in testSuccessfulTracing and testSuccessfulCpuProfiling.
     */
    private void tapShareNotification() throws Exception {
        mDevice.openNotification();
        UiObject2 shareNotification = mDevice.wait(Until.findObject(
                By.text("Tap to share your recording")),
                UI_TIMEOUT_MS);
        assertNotNull("Share notification not found.", shareNotification);
        shareNotification.click();

        mDevice.waitForIdle();
    }

    /**
     * This method clicks through the share dialog steps.
     * This is only used in testSuccessfulTracing and testSuccessfulCpuProfiling.
     */
    private void clickThroughShareSteps() throws Exception {
        UiObject2 shareDialog = mDevice.wait(Until.findObject(
                By.textContains("Only share system traces with people and apps you trust.")),
                UI_TIMEOUT_MS);
        assertNotNull("Share dialog not found.", shareDialog);

        // The buttons on dialogs sometimes have their capitalization manipulated by themes.
        UiObject2 shareButton = mDevice.wait(Until.findObject(
                By.text(Pattern.compile("share", Pattern.CASE_INSENSITIVE))), UI_TIMEOUT_MS);
        assertNotNull("Share button not found.", shareButton);
        shareButton.click();

        // The share sheet will not appear on AOSP builds, as there are no apps available to share
        // traces with. This checks if Gmail is installed (i.e. if the build is non-AOSP) before
        // verifying that the share sheet exists.
        try {
            Context context = InstrumentationRegistry.getContext();
            context.getPackageManager().getApplicationInfo("com.google.android.gm", 0);
            UiObject2 shareSheet = mDevice.wait(Until.findObject(
                    By.res("android:id/profile_tabhost")), UI_TIMEOUT_MS);
            assertNotNull("Share sheet not found.", shareSheet);
        } catch (PackageManager.NameNotFoundException e) {
            // Gmail is not installed, so the device is on an AOSP build.
        }
    }

    /**
     * Taps on the 'Categories' button.
     */
    private void openTraceCategories() throws Exception {
        UiObject2 categoriesButton = findObjectOnMainScreenByText("Categories");
        assertNotNull("Categories button not found.", categoriesButton);
        categoriesButton.click();

        mDevice.waitForIdle();
    }

    /**
     * Taps on the 'Restore default categories' button.
     */
    private void restoreDefaultCategories() throws Exception {
        UiObject2 restoreButton = findObjectOnMainScreenByText("Restore default categories");
        assertNotNull("Restore default categories button not found.", restoreButton);
        restoreButton.click();

        mDevice.waitForIdle();
        // This pause is necessary because the trace category restoration takes time to propagate to
        // the main page.
        SystemClock.sleep(SHORT_PAUSE_MS);
    }

    /**
     * Returns the UiObject2 of the summary for 'Categories'.
     * This must only be used on Traceur's main page.
     */
    private UiObject2 getCategoriesSummary() throws Exception {
        UiObject2 categoriesButton = findObjectOnMainScreenByText("Categories");
        assertNotNull("Categories button not found.", categoriesButton);

        // The summary text is a sibling view of 'Categories' and can be found through their parent.
        UiObject2 categoriesSummary = categoriesButton.getParent().wait(Until.findObject(
                By.res("android:id/summary")), UI_TIMEOUT_MS);
        assertNotNull("Categories summary not found.", categoriesSummary);
        return categoriesSummary;
    }

    /**
     * Returns the list of available trace categories.
     * This must only be used after openTraceCategories() has been called.
     */
    private List<UiObject2> getTraceCategories() {
        UiObject2 categoriesListView = mDevice.wait(Until.findObject(
                By.res("android:id/select_dialog_listview")), UI_TIMEOUT_MS);
        assertNotNull("List of categories not found.", categoriesListView);
        return categoriesListView.getChildren();
    }

    /**
     * Toggles the first checkbox in the list of trace categories.
     * This must only be used after openTraceCategories() has been called.
     */
    private void toggleFirstTraceCategory() throws Exception {
        getTraceCategories().get(0).click();

        mDevice.waitForIdle();

        UiObject2 confirmButton = mDevice.wait(Until.findObject(
                By.res("android:id/button1")), UI_TIMEOUT_MS);
        assertNotNull("'OK' button not found under trace categories list.", confirmButton);
        confirmButton.click();

        mDevice.waitForIdle();
        // This pause is necessary because the trace category selection takes time to propagate to
        // the main page.
        SystemClock.sleep(SHORT_PAUSE_MS);
    }

}
