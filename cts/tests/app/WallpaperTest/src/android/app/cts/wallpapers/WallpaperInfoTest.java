/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.cts.wallpapers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.WallpaperInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.wallpaper.WallpaperService;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WallpaperInfoTest {

    @Test
    public void test_wallpaperServiceQuery() {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("android.app.cts.wallpapers");
        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        assertThat(result).hasSize(3);
    }

    @Test
    public void test_wallpaperInfoOptions() {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();

        WallpaperInfo wallpaperInfo = getInfoForService(TestLiveWallpaper.class);

        assertThat(context.getString(R.string.wallpaper_title))
                .isEqualTo(wallpaperInfo.loadLabel(pm));
        assertThat(context.getString(R.string.wallpaper_description))
                .isEqualTo(wallpaperInfo.loadDescription(pm));
        assertThat(context.getString(R.string.wallpaper_collection))
                .isEqualTo(wallpaperInfo.loadAuthor(pm));
        assertThat(context.getString(R.string.wallpaper_context))
                .isEqualTo(wallpaperInfo.loadContextDescription(pm));
        assertThat(context.getString(R.string.wallpaper_context_uri))
                .isEqualTo(wallpaperInfo.loadContextUri(pm).toString());
        assertThat(context.getString(R.string.wallpaper_slice_uri))
                .isEqualTo(wallpaperInfo.getSettingsSliceUri().toString());
        assertThat(wallpaperInfo.getShowMetadataInPreview()).isTrue();
        assertThat(wallpaperInfo.supportsMultipleDisplays()).isTrue();
        assertThat(wallpaperInfo.shouldUseDefaultUnfoldTransition()).isTrue();
        assertThat(wallpaperInfo.loadIcon(pm)).isNotNull();
        assertThat(wallpaperInfo.loadThumbnail(pm)).isNotNull();
    }

    @Test
    public void test_defaultUnfoldTransitionDisabled() {
        WallpaperInfo wallpaperInfo = getInfoForService(TestLiveWallpaperNoUnfoldTransition.class);

        assertThat(wallpaperInfo.shouldUseDefaultUnfoldTransition()).isFalse();
    }

    private <T extends WallpaperService> WallpaperInfo getInfoForService(Class<T> service) {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("android.app.cts.wallpapers");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        ResolveInfo info = null;
        for (int i = 0; i < result.size(); i++) {
            ResolveInfo resolveInfo = result.get(i);
            if (resolveInfo.serviceInfo.name.equals(service.getName())) {
                info = resolveInfo;
                break;
            }
        }

        assertWithMessage(service.getName() + " was not found in the queried "
                + "wallpaper services list " + result).that(info).isNotNull();

        try {
            return new WallpaperInfo(context, info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
