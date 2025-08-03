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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.app.Components.CRASHING_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.view.cts.surfacevalidator.IAttachEmbeddedWindow;
import android.window.SurfaceSyncGroup;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Presubmit
public class SurfaceSyncGroupTests {
    private static final String TAG = "SurfaceSyncGroupTests";

    @Rule
    public ActivityTestRule<SurfaceSyncGroupActivity> mActivityRule = new ActivityTestRule<>(
            SurfaceSyncGroupActivity.class);

    private SurfaceSyncGroupActivity mActivity;

    Instrumentation mInstrumentation;

    private SettingsSession<Integer> mHideDialogSetting;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        // One of the tests purposely crashes so disable showing the crash dialog to avoid breaking
        // tests later on.
        mHideDialogSetting = new SettingsSession<>(
                Settings.Global.getUriFor(Settings.Global.HIDE_ERROR_DIALOGS),
                Settings.Global::getInt, Settings.Global::putInt);
        mHideDialogSetting.set(1);
    }

    @After
    public void tearDown() {
        if (mHideDialogSetting != null) mHideDialogSetting.close();
        ActivityManager am = mActivity.getSystemService(ActivityManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> am.forceStopPackage(
                CRASHING_ACTIVITY.getPackageName()));
    }

    @Test
    public void testProcessCrash() {
        var data = mActivity.setupEmbeddedSCVH();
        SurfacePackage surfacePackage = data.first;
        IAttachEmbeddedWindow iAttachEmbeddedWindow = data.second;

        CountDownLatch finishedRunLatch = new CountDownLatch(1);

        mActivity.runOnUiThread(() -> {
            SurfaceSyncGroup surfaceSyncGroup = new SurfaceSyncGroup(TAG);
            surfaceSyncGroup.add(surfacePackage, () -> {
                try {
                    iAttachEmbeddedWindow.sendCrash();
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send crash to embedded");
                }
            });
            surfaceSyncGroup.add(mActivity.getWindow().getRootSurfaceControl(),
                    null /* runnable */);
            // Add a transaction committed listener to make sure the transaction has been applied
            // even though one of the processes involved crashed.
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, finishedRunLatch::countDown);
            surfaceSyncGroup.addTransaction(t);
            surfaceSyncGroup.markSyncReady();
        });

        try {
            finishedRunLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for transaction committed callback");
        }

        assertEquals("Failed to apply transaction for SurfaceSyncGroup", 0,
                finishedRunLatch.getCount());
    }

    /**
     * This test will ensure that if multiple SurfaceSyncGroups are crated for the same ViewRootImpl
     * the SurfaceSyncGroups will maintain an order. The scenario that could occur is the following:
     * 1. SSG1 is created that includes the target VRI. There could be other VRIs in SSG1
     * 2. target VRI draws its frame and is ready, but SSG1 is still waiting on other things in the
     * SSG
     * 3. Another SSG2 is created for the target VRI. The second frame renders and is ready and this
     * SSG has nothing else to wait on. SSG2 will apply at this point, even though SSG1 has not
     * 4. Frame2 will get to SF first and possibly later Frame1 will get to SF when SSG1 completes.
     * <p>
     * This test forces that behavior by ensuring the first SSG does not complete until the second
     * SSG gets its buffer and is considered complete. It waits a bit to ensure it had time to
     * make it to SF first. Then later it marks the first SSG as ready so that buffer could get
     * sent to SF.
     * <p>
     * With the fix in VRI, the second SSG will not complete until the first SSG has been submitted
     * to SF, ensuring that the frames are submitted in order.
     */
    @Test
    public void testOverlappingSyncsEnsureOrder() throws InterruptedException {
        CountDownLatch secondDrawCompleteLatch = new CountDownLatch(1);
        CountDownLatch bothSyncGroupsComplete = new CountDownLatch(2);
        final SurfaceSyncGroup firstSsg = new SurfaceSyncGroup(TAG + "-first");
        final SurfaceSyncGroup secondSsg = new SurfaceSyncGroup(TAG + "-second");

        View backgroundView = mActivity.getBackgroundView();
        mActivity.runOnUiThread(() -> {
            firstSsg.add(backgroundView.getRootSurfaceControl(),
                    () -> backgroundView.setBackgroundColor(Color.RED));
            addSecondSyncGroup(secondSsg, secondDrawCompleteLatch, bothSyncGroupsComplete);
        });

        assertTrue("Failed to draw two frames", secondDrawCompleteLatch.await(5, TimeUnit.SECONDS));

        // Add a bit of a delay to make sure the second frame could have been sent to SF
        Thread.sleep(HW_TIMEOUT_MULTIPLIER * 32L);

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(Runnable::run, bothSyncGroupsComplete::countDown);
        firstSsg.addTransaction(t);
        firstSsg.markSyncReady();

        assertTrue("Failed to wait for both SurfaceSyncGroups to apply",
                bothSyncGroupsComplete.await(HW_TIMEOUT_MULTIPLIER * 5L, TimeUnit.SECONDS));

        validateScreenshot();
    }

    private void addSecondSyncGroup(SurfaceSyncGroup surfaceSyncGroup,
            CountDownLatch waitForSecondDraw, CountDownLatch bothSyncGroupsComplete) {
        View backgroundView = mActivity.getBackgroundView();
        ViewTreeObserver viewTreeObserver = backgroundView.getViewTreeObserver();
        viewTreeObserver.registerFrameCommitCallback(() -> mActivity.runOnUiThread(() -> {
            surfaceSyncGroup.add(backgroundView.getRootSurfaceControl(),
                    () -> backgroundView.setBackgroundColor(Color.BLUE));
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, bothSyncGroupsComplete::countDown);
            surfaceSyncGroup.addTransaction(t);
            surfaceSyncGroup.markSyncReady();

            viewTreeObserver.registerFrameCommitCallback(waitForSecondDraw::countDown);
        }));
    }

    private void validateScreenshot() {
        Bitmap screenshot = mInstrumentation.getUiAutomation().takeScreenshot(
                mActivity.getWindow());
        assertNotNull("Failed to generate a screenshot", screenshot);
        Bitmap swBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, false);
        screenshot.recycle();

        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLUE);
        int halfWidth = swBitmap.getWidth() / 2;
        int halfHeight = swBitmap.getHeight() / 2;
        // We don't need to check all the pixels since we only care that at least some of them are
        // blue. If the buffers were submitted out of order, all the pixels will be red.
        Rect bounds = new Rect(halfWidth, halfHeight, halfWidth + 10, halfHeight + 10);
        int numMatchingPixels = pixelChecker.getNumMatchingPixels(swBitmap, bounds);
        assertEquals("Expected 100 received " + numMatchingPixels + " matching pixels", 100,
                numMatchingPixels);

        swBitmap.recycle();
    }
}
