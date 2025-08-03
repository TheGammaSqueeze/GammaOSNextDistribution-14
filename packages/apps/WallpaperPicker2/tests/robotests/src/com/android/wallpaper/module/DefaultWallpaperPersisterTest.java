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
package com.android.wallpaper.module;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;

import static com.android.wallpaper.module.WallpaperPersister.DEST_BOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.DefaultWallpaperPersisterTest.TestSetWallpaperCallback.SetWallpaperStatus;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.module.logging.TestUserEventLogger;
import com.android.wallpaper.testing.TestAsset;
import com.android.wallpaper.testing.TestBitmapCropper;
import com.android.wallpaper.testing.TestCurrentWallpaperInfoFactory;
import com.android.wallpaper.testing.TestInjector;
import com.android.wallpaper.testing.TestStaticWallpaperInfo;
import com.android.wallpaper.testing.TestWallpaperPreferences;
import com.android.wallpaper.testing.TestWallpaperStatusChecker;
import com.android.wallpaper.util.DisplayUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.shadows.ShadowPausedAsyncTask;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class DefaultWallpaperPersisterTest {
    private static final String TAG = "DefaultWallpaperPersisterTest";
    private static final String ACTION_URL = "http://google.com";

    private Context mContext;
    /** DefaultWallpaperPersister object under test */
    private DefaultWallpaperPersister mPersister;
    /** Spy on real WallpaperManager instance  */
    private WallpaperManager mManager;
    /** Fake instance of WallpaperPreferences */
    private TestWallpaperPreferences mPrefs;
    /** Executor to use for AsyncTask */
    private final PausedExecutorService mPausedExecutor = new PausedExecutorService();

    @Before
    public void setUp() {
        InjectorProvider.setInjector(new TestInjector(new TestUserEventLogger()));
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mManager = spy(WallpaperManager.getInstance(mContext));
        mPrefs = new TestWallpaperPreferences();
        WallpaperChangedNotifier changedNotifier = spy(WallpaperChangedNotifier.getInstance());
        DisplayUtils displayUtils = new DisplayUtils(mContext);
        TestBitmapCropper cropper = new TestBitmapCropper();
        TestWallpaperStatusChecker statusChecker = new TestWallpaperStatusChecker();
        TestCurrentWallpaperInfoFactory wallpaperInfoFactory =
                new TestCurrentWallpaperInfoFactory(mContext);

        mPersister = new DefaultWallpaperPersister(mContext, mManager, mPrefs, changedNotifier,
                displayUtils, cropper, statusChecker, wallpaperInfoFactory, false);
    }

    @Test
    public void isSeparateLockScreenWallpaperSet_trueIfSet() {
        doReturn(-1).when(mManager).getWallpaperId(FLAG_LOCK);

        assertThat(mPersister.getDefaultWhichWallpaper()).isEqualTo(FLAG_SYSTEM | FLAG_LOCK);
    }

    @Test
    public void isSeparateLockScreenWallpaperSet_falseIfUnset() {
        doReturn(1).when(mManager).getWallpaperId(FLAG_LOCK);

        assertThat(mPersister.getDefaultWhichWallpaper()).isEqualTo(FLAG_SYSTEM);
    }

    @Test
    public void setBitmapWallpaper_succeeds() {
        TestStaticWallpaperInfo wallpaperInfo = newStaticWallpaperInfo();
        prepareWallpaperSetFromInfo(wallpaperInfo);
        TestSetWallpaperCallback callback = new TestSetWallpaperCallback();

        mPersister.setIndividualWallpaper(wallpaperInfo, wallpaperInfo.getAsset(mContext), null,
                1.0f, DEST_BOTH, callback);

        verifyWallpaperSetSuccess(callback);
    }

    @Test
    public void setBitmapWallpaper_setsActionUrl() {
        TestStaticWallpaperInfo wallpaperInfo = newStaticWallpaperInfo();
        prepareWallpaperSetFromInfo(wallpaperInfo);
        TestSetWallpaperCallback callback = new TestSetWallpaperCallback();

        mPersister.setIndividualWallpaper(wallpaperInfo, wallpaperInfo.getAsset(mContext), null,
                1.0f, DEST_BOTH, callback);

        verifyWallpaperSetSuccess(callback);
        assertThat(mPrefs.getHomeWallpaperActionUrl()).isEqualTo(ACTION_URL);
        assertThat(mPrefs.getLockWallpaperActionUrl()).isEqualTo(ACTION_URL);
    }

     // Creates a basic test wallpaper info instance.
    private static TestStaticWallpaperInfo newStaticWallpaperInfo() {
        List<String> attributions = new ArrayList<>();
        attributions.add("Title");
        attributions.add("Subtitle 1");
        attributions.add("Subtitle 2");
        TestStaticWallpaperInfo wallpaperInfo = new TestStaticWallpaperInfo(
                TestStaticWallpaperInfo.COLOR_DEFAULT);
        wallpaperInfo.setAttributions(attributions);
        wallpaperInfo.setCollectionId("collectionStatic");
        wallpaperInfo.setWallpaperId("wallpaperStatic");
        wallpaperInfo.setActionUrl(ACTION_URL);
        return wallpaperInfo;
    }

    // Call this method to prepare for a call to setIndividualWallpaper with non-streamable bitmap.
    private void prepareWallpaperSetFromInfo(TestStaticWallpaperInfo wallpaperInfo) {
        // Retrieve the bitmap to be set by the given WallpaperInfo, and override the return value
        // from WallpaperManager.getDrawable().
        TestAsset asset = (TestAsset) wallpaperInfo.getAsset(mContext);
        doReturn(new BitmapDrawable(mContext.getResources(), asset.getBitmap())).when(mManager)
                .getDrawable();
        // Override the background executor for AsyncTask to that we can explicitly execute its
        // tasks - otherwise this will remain in the queue even after main looper idle.
        ShadowPausedAsyncTask.overrideExecutor(mPausedExecutor);
    }

    private void verifyWallpaperSetSuccess(TestSetWallpaperCallback callback) {
        // Execute pending Asset#decodeBitmap; queues SetWallpaperTask background job
        shadowMainLooper().idle();
        // Execute SetWallpaperTask background job; queues onPostExecute on main thread
        mPausedExecutor.runAll();
        // Execute SetWallpaperTask#onPostExecute
        shadowMainLooper().idle();

        assertThat(callback.getStatus()).isEqualTo(SetWallpaperStatus.SUCCESS);
    }

    /**
     * Simple wallpaper callback to either record success or log on failure.
     */
    static class TestSetWallpaperCallback implements SetWallpaperCallback {
        enum SetWallpaperStatus {
            UNCALLED,
            SUCCESS,
            FAILURE
        }
        SetWallpaperStatus mStatus = SetWallpaperStatus.UNCALLED;
        @Override
        public void onSuccess(WallpaperInfo wallpaperInfo, int destination) {
            mStatus = SetWallpaperStatus.SUCCESS;
        }

        @Override
        public void onError(@Nullable Throwable throwable) {
            mStatus = SetWallpaperStatus.FAILURE;
            Log.e(TAG, "Set wallpaper failed", throwable);
        }

        public SetWallpaperStatus getStatus() {
            return mStatus;
        }
    }
}
