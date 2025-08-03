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
package android.sharesheet.cts;

import static android.Manifest.permission.SHOW_CUSTOMIZED_RESOLVER;
import static android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.nfc.Flags;
import android.nfc.NfcAdapter;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CTS tests for the NFC-customized resolver.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
@RunWith(AndroidJUnit4.class)
public class CtsNfcResolverDeviceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            START_ACTIVITIES_FROM_BACKGROUND, SHOW_CUSTOMIZED_RESOLVER);

    public static final String TAG = CtsNfcResolverDeviceTest.class.getSimpleName();

    private static final int WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5000;
    private static final int WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS = 2500;
    private static final int WAIT_FOR_IDLE_TIMEOUT_MS = 5000;

    private static final String CTS_DATA_TYPE = "test/cts"; // Special CTS mime type

    private Context mContext;
    private Instrumentation mInstrumentation;
    private UiAutomation mAutomation;
    public UiDevice mDevice;
    private UiObject2 mSharesheet;

    private String mSharesheetPkg;

    private ActivityManager mActivityManager;

    private String mAppLabel,
            mActivityTesterAppLabel, mActivityTesterActivityLabel,
            mIntentFilterTesterAppLabel, mIntentFilterTesterIntentFilterLabel;


    private static Intent createNfcResolverIntent(
            Intent target,
            CharSequence title,
            List<ResolveInfo> resolutionList) {
        Intent resolverIntent = new Intent(NfcAdapter.ACTION_SHOW_NFC_RESOLVER);
        resolverIntent.putExtra(Intent.EXTRA_INTENT, target);
        resolverIntent.putExtra(Intent.EXTRA_TITLE, title);
        resolverIntent.putParcelableArrayListExtra(
                NfcAdapter.EXTRA_RESOLVE_INFOS, new ArrayList<>(resolutionList));
        return resolverIntent;
    }

    /**
     * To validate Sharesheet API and API behavior works as intended, UI tests are required. It is
     * impossible to know how the Sharesheet UI will be modified by end partners, so these tests
     * attempt to assume use the minimum needed assumptions to make the tests work.
     *
     * We cannot assume a scrolling direction or starting point because of potential UI variations.
     * Because of limits of the UiAutomator pipeline only content visible on screen can be tested.
     * These two constraints mean that all automated Sharesheet tests must be for content we
     * reasonably expect to be visible after the sheet is opened without any direct interaction.
     *
     * Extra care is taken to ensure tested content is reasonably visible by:
     * - Splitting tests across multiple Sharesheet calls
     * - Excluding all packages not relevant to the test
     * - Assuming a max of three targets per row of apps
     */

    @Before
    public void init() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mContext = mInstrumentation.getTargetContext();

        mActivityManager = mContext.getSystemService(ActivityManager.class);
        PackageManager pm = mContext.getPackageManager();
        assertNotNull(mActivityManager);
        assertNotNull(pm);

        // Load in string to match against
        mAppLabel = mContext.getString(R.string.test_app_label);
        mActivityTesterAppLabel = mContext.getString(R.string.test_activity_label_app);
        mActivityTesterActivityLabel = mContext.getString(R.string.test_activity_label_activity);
        mIntentFilterTesterAppLabel = mContext.getString(R.string.test_intent_filter_label_app);
        mIntentFilterTesterIntentFilterLabel =
                mContext.getString(R.string.test_intent_filter_label_intentfilter);

        // We need to know the package used by the system Sharesheet so we can properly
        // wait for the UI to load. Do this by resolving which activity consumes the share intent.
        // There must be a system Sharesheet or fail, otherwise fetch its package.
        Intent shareIntent =
                createNfcResolverIntent(new Intent(), null, new ArrayList<>());
        ResolveInfo shareRi = pm.resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

        assertNotNull(shareRi);
        assertNotNull(shareRi.activityInfo);

        mSharesheetPkg = shareRi.activityInfo.packageName;
        assertNotNull(mSharesheetPkg);

        // Finally ensure the device is awake
        mDevice.wakeUp();
    }

    @Test
    public void testNfcCustomizations() {
        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<Intent> targetLaunchIntent = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            targetLaunchIntent.set(intent);
            appStarted.countDown();
        });

        final String title = "custom title";
        Intent sendIntent = createMatchingIntent();
        List<ResolveInfo> matchingTargets = mContext.getPackageManager().queryIntentActivities(
                sendIntent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA
        );

        // Filter to include only the targets that we consider in our test design. Also filter out
        // the "exclude tester" which would otherwise be included (i.e., otherwise no conditions
        // would've been responsible for specifying its exclusion), because we want to ensure that
        // the relevant test targets are on screen, and so we must limit the size of the list. Note
        // that the "customized chooser" API doesn't *require* that no other targets are displayed,
        // but we do test (at least for now) that *at least* the specified targets are included.
        final List<ResolveInfo> newTargets = matchingTargets
                .stream()
                .filter(t ->
                        t.activityInfo.packageName.startsWith("android.sharesheet.cts")
                        && !t.activityInfo.packageName.contains("excludetester"))
                .collect(Collectors.toList());

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent resolverIntent = createNfcResolverIntent(
                    sendIntent, title, newTargets);
            resolverIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(resolverIntent);

            waitAndAssertPkgVisible(mSharesheetPkg);
            mSharesheet = mDevice.findObject(By.pkg(mSharesheetPkg).depth(0));
            waitForIdle();

            waitAndAssertTextContains(title);

            showsApplicationLabel();
            showsAppAndActivityLabel();
            showsAppAndIntentFilterLabel();

            UiObject2 shareTarget = findTextContains(mAppLabel);
            assertNotNull(shareTarget);
            // Start the event sequence and wait for results
            // Must be run last, partial completion closes the Sharesheet
            shareTarget.click();

            appStarted.await(1000, TimeUnit.MILLISECONDS);
            assertEquals(CTS_DATA_TYPE, targetLaunchIntent.get().getType());
            assertEquals(Intent.ACTION_SEND, targetLaunchIntent.get().getAction());
        }, () -> {
            // The Sharesheet may or may not be open depending on test success, close it if it is.
            closeSharesheetIfNeeded();
            });
    }

    /*
    Test methods
     */

    /**
     * Tests API behavior compliance for security to always show application label
     */
    private void showsApplicationLabel() {
        // For each app target the providing app's application manifest label should be shown
        waitAndAssertTextContains(mAppLabel);
    }

    /**
     * Tests API behavior compliance to show application and activity label when available
     */
    private void showsAppAndActivityLabel() {
        waitAndAssertTextContains(mActivityTesterAppLabel);
        waitAndAssertTextContains(mActivityTesterActivityLabel);
    }

    /**
     * Tests API behavior compliance to show application and intent filter label when available
     */
    private void showsAppAndIntentFilterLabel() {
        // NOTE: it is not necessary to show any set Activity label if an IntentFilter label is set
        waitAndAssertTextContains(mIntentFilterTesterAppLabel);
        waitAndAssertTextContains(mIntentFilterTesterIntentFilterLabel);
    }

    /*
    Setup methods
     */

    private void closeSharesheetIfNeeded() {
        if (isSharesheetVisible()) closeSharesheet();
    }

    private void closeSharesheet() {
        mDevice.pressBack();
        waitAndAssertPkgNotVisible(mSharesheetPkg);
        waitForIdle();
    }

    private boolean isSharesheetVisible() {
        // This method intentionally does not wait, looks to see if visible on method call
        try {
            return mDevice.findObject(By.pkg(mSharesheetPkg).depth(0)) != null;
        } catch (StaleObjectException e) {
            // If we get a StaleObjectException, it means that the underlying View has
            // already been destroyed, meaning the sharesheet is no longer visible.
            return false;
        }
    }

    private Intent createMatchingIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(CTS_DATA_TYPE);
        return intent;
    }

    /*
    UI testing methods
     */

    private void waitForIdle() {
        mDevice.waitForIdle(WAIT_FOR_IDLE_TIMEOUT_MS);
    }

    private void waitAndAssertPkgVisible(String pkg) {
        waitAndAssertFoundOnDevice(By.pkg(pkg).depth(0));
    }

    private void waitAndAssertPkgNotVisible(String pkg) {
        waitAndAssertNotFoundOnDevice(By.pkg(pkg));
    }

    private void waitAndAssertTextContains(String containsText) {
        waitAndAssertTextContains(containsText, false);
    }

    private void waitAndAssertTextContains(String text, boolean caseSensitive) {
        waitAndAssertFound(By.text(textContainsPattern(text, caseSensitive)));
    }

    private static Pattern textContainsPattern(String text, boolean caseSensitive) {
        int flags = Pattern.DOTALL;
        if (!caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        return Pattern.compile(String.format("^.*%s.*$", Pattern.quote(text)), flags);
    }

    /**
     * waitAndAssertFound will wait until UI within sharesheet defined by the selector is found. If
     * it's never found, this will wait for the duration of the full timeout. Take care to call this
     * method after reasonable steps are taken to ensure fast completion.
     */
    private void waitAndAssertFound(BySelector selector) {
        assertNotNull(mSharesheet.wait(Until.findObject(selector),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS));
    }

    /**
     * Same as waitAndAssertFound but searching the entire device UI.
     */
    private void waitAndAssertFoundOnDevice(BySelector selector) {
        assertNotNull(mDevice.wait(Until.findObject(selector), WAIT_AND_ASSERT_FOUND_TIMEOUT_MS));
    }

    /**
     * waitAndAssertNotFound waits for any visible UI within sharesheet to be hidden, validates that
     * it's indeed gone without waiting more and returns. This means if the UI wasn't visible to
     * start with the method will return without no timeout. Take care to call this method only once
     * there's reason to think the UI is in the right state for testing.
     */
    private void waitAndAssertNotFound(BySelector selector) {
        mSharesheet.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertNull(mSharesheet.findObject(selector));
    }

    /**
     * Same as waitAndAssertNotFound() but searching the entire device UI.
     */
    private void waitAndAssertNotFoundOnDevice(BySelector selector) {
        mDevice.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertNull(mDevice.findObject(selector));
    }

    /**
     * findTextContains uses logic similar to waitAndAssertFound to locate UI objects that contain
     * the provided String.
     * @param containsText the String to search for, note this is not an exact match only contains
     * @return UiObject2 that can be used, for example, to execute a click
     */
    private UiObject2 findTextContains(String containsText) {
        return mSharesheet.wait(Until.findObject(By.textContains(containsText)),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
    }

    /**
     * A {@link Runnable}-like interface that's declared to throw checked exceptions. This is
     * provided for convenience in writing inline ("lambda") blocks, so that test code doesn't need
     * extra boilerplate to handle every possible site of a checked exception (since we're going to
     * end up propagating these exceptions as test failures anyways).
     */
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Perform the requested {@code execution} (which may throw), but then perform the requested
     * {@code cleanup} (whether or not the main execution succeeded) before potentially throwing any
     * exception from the main execution. This is similar to the normal `try/finally` construct,
     * except that the `finally` (or `cleanup`) step is executed <em>before</em> any stack-unwinding
     * to try to catch the exception. Note that any re-thrown exception is wrapped as a
     * {@link RuntimeException} so that clients can skip the checked-exception boilerplate.
     * TODO: it may be possible to move all our cleanup steps to an `@After` method and avoid this
     * unusual construct, but we'd have to refactor to unify the cleanup logic across all tests.
     */
    private static void runAndExecuteCleanupBeforeAnyThrow(
            ThrowingRunnable execution, Runnable cleanup) {
        Throwable exceptionToRethrow = null;
        try {
            execution.run();
        } catch (Throwable mainExecutionException) {
            exceptionToRethrow = mainExecutionException;
        } finally {
            try {
                cleanup.run();
            } catch (Throwable cleanupException) {
                if (exceptionToRethrow == null) {
                    exceptionToRethrow = cleanupException;
                } else {
                    exceptionToRethrow.addSuppressed(cleanupException);
                }
            }

            if (exceptionToRethrow != null) {
                throw new RuntimeException(exceptionToRethrow);
            }
        }
    }
}
