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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.SET_WALLPAPER;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SET_WALLPAPER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.graphics.Bitmap;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.Wallpaper;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BitmapUtils;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

// TODO (b/284309054): Add test for WallpaperManager#setResource
@RunWith(BedsteadJUnit4.class)
public final class WallpaperTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Bitmap sOriginalWallpaper = TestApis.wallpaper().getBitmap();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final Bitmap sReferenceWallpaper = BitmapUtils.generateRandomBitmap(97, 73);
    private static final InputStream sReferenceWallpaperStream =
            BitmapUtils.bitmapToInputStream(sReferenceWallpaper);

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @PolicyAppliesTest(policy = Wallpaper.class)
    public void setBitmap_viaDpc_disallowed_canSet() throws Exception {
        try {
            sDeviceState.dpc().wallpaperManager().setBitmap(sReferenceWallpaper);

            Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                    .toMeet((bitmap) ->
                            BitmapUtils.compareBitmaps(bitmap, sReferenceWallpaper))
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().wallpaperManager().setBitmap(sOriginalWallpaper);
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @PolicyDoesNotApplyTest(policy = Wallpaper.class)
    public void setBitmap_viaDpc_disallowed_cannotSet() throws Exception {
        try {
            sDeviceState.dpc().wallpaperManager().setBitmap(sReferenceWallpaper);

            Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                    .toMeet((bitmap) ->
                            BitmapUtils.compareBitmaps(bitmap, sOriginalWallpaper))
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().wallpaperManager().setBitmap(sOriginalWallpaper);
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setBitmap_allowed_canSet() throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install()) {
            testAppInstance.wallpaperManager().setBitmap(sReferenceWallpaper);

            Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                    .toMeet((bitmap) ->
                            BitmapUtils.compareBitmaps(bitmap, sReferenceWallpaper))
                    .errorOnFail()
                    .await();
        } finally {
            TestApis.wallpaper().setBitmap(sOriginalWallpaper);
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setBitmap")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setBitmap_disallowed_cannotSet() throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install()) {
            testAppInstance.wallpaperManager().setBitmap(sReferenceWallpaper);

            Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                    .toMeet((bitmap) ->
                            BitmapUtils.compareBitmaps(bitmap, sOriginalWallpaper))
                    .errorOnFail()
                    .await();
        }
    }

    @ApiTest(apis = "android.app.WallpaperManager#setStream")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureDoesNotHaveUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setStream_allowed_canSet() {
        TestApis.wallpaper().setStream(sReferenceWallpaperStream);

        Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                .toMeet((bitmap) ->
                        BitmapUtils.compareBitmaps(bitmap, sReferenceWallpaper))
                .errorOnFail()
                .await();
    }

    @ApiTest(apis = "android.app.WallpaperManager#setStream")
    @EnsureHasPermission(SET_WALLPAPER)
    @EnsureHasUserRestriction(DISALLOW_SET_WALLPAPER)
    @Test
    public void setStream_disallowed_cannotSet() {
        TestApis.wallpaper().setStream(sReferenceWallpaperStream);

        Poll.forValue("wallpaper bitmap", () -> TestApis.wallpaper().getBitmap())
                .toMeet((bitmap) ->
                        BitmapUtils.compareBitmaps(bitmap, sOriginalWallpaper))
                .errorOnFail()
                .await();
    }
}
