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

package android.display.cts;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.DisplayMetrics;
import android.view.Surface;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class VirtualDisplayConfigTest {

    private static final String NAME = "VirtualDisplayConfigTest";
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;
    private static final int DENSITY = DisplayMetrics.DENSITY_MEDIUM;
    private static final float REQUESTED_REFRESH_RATE = 30.0f;
    private static final int FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;

    @Test
    public void parcelAndUnparcel_matches() {
        final boolean customHomeEnabled = android.companion.virtual.flags.Flags.vdmCustomHome();
        final Surface surface = new Surface(new SurfaceTexture(/*texName=*/1));
        VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY)
                        .setFlags(FLAGS)
                        .setSurface(surface)
                        .setDisplayCategories(Set.of("C1", "C2"))
                        .addDisplayCategory("C3")
                        .setRequestedRefreshRate(REQUESTED_REFRESH_RATE);
        if (customHomeEnabled) {
            builder.setHomeSupported(true);
        }
        final VirtualDisplayConfig originalConfig = builder.build();

        assertThat(originalConfig.getName()).isEqualTo(NAME);
        assertThat(originalConfig.getWidth()).isEqualTo(WIDTH);
        assertThat(originalConfig.getHeight()).isEqualTo(HEIGHT);
        assertThat(originalConfig.getDensityDpi()).isEqualTo(DENSITY);
        assertThat(originalConfig.getFlags()).isEqualTo(FLAGS);
        assertThat(originalConfig.getSurface()).isEqualTo(surface);
        assertThat(originalConfig.getDisplayCategories()).containsExactly("C1", "C2", "C3");
        assertThat(originalConfig.getRequestedRefreshRate()).isEqualTo(REQUESTED_REFRESH_RATE);
        if (customHomeEnabled) {
            assertThat(originalConfig.isHomeSupported()).isEqualTo(true);
        }

        final Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualDisplayConfig recreatedConfig =
                VirtualDisplayConfig.CREATOR.createFromParcel(parcel);

        assertThat(recreatedConfig.getName()).isEqualTo(NAME);
        assertThat(recreatedConfig.getWidth()).isEqualTo(WIDTH);
        assertThat(recreatedConfig.getHeight()).isEqualTo(HEIGHT);
        assertThat(recreatedConfig.getDensityDpi()).isEqualTo(DENSITY);
        assertThat(recreatedConfig.getFlags()).isEqualTo(FLAGS);
        assertThat(recreatedConfig.getSurface()).isNotNull();
        assertThat(recreatedConfig.getDisplayCategories()).containsExactly("C1", "C2", "C3");
        assertThat(recreatedConfig.getRequestedRefreshRate()).isEqualTo(REQUESTED_REFRESH_RATE);
        if (customHomeEnabled) {
            assertThat(recreatedConfig.isHomeSupported()).isEqualTo(true);
        }
    }

    @Test
    public void virtualDisplayConfig_onlyRequiredFields() {
        final boolean customHomeEnabled = android.companion.virtual.flags.Flags.vdmCustomHome();
        final VirtualDisplayConfig config =
                new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY).build();

        assertThat(config.getFlags()).isEqualTo(0);
        assertThat(config.getSurface()).isNull();
        assertThat(config.getDisplayCategories()).isEmpty();
        assertThat(config.getRequestedRefreshRate()).isEqualTo(0.0f);
        if (customHomeEnabled) {
            assertThat(config.isHomeSupported()).isFalse();
        }
    }

    @Test
    public void virtualDisplayConfig_noName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new VirtualDisplayConfig.Builder(null, WIDTH, HEIGHT, DENSITY);
        });
    }

    @Test
    public void virtualDisplayConfig_invalidWidth_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new VirtualDisplayConfig.Builder(NAME, 0, HEIGHT, DENSITY);
        });
    }

    @Test
    public void virtualDisplayConfig_invalidHeight_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new VirtualDisplayConfig.Builder(NAME, WIDTH, 0, DENSITY);
        });
    }

    @Test
    public void virtualDisplayConfig_invalidDensity_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new VirtualDisplayConfig.Builder(null, WIDTH, HEIGHT, 0);
        });
    }

    @Test
    public void virtualDisplayConfig_nullDisplayCategories_throwsException() {
        assertThrows(NullPointerException.class, () -> {
            new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY)
                    .setDisplayCategories(null);
        });
    }

    @Test
    public void virtualDisplayConfig_nullDisplayCategory_throwsException() {
        assertThrows(NullPointerException.class, () -> {
            new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY)
                    .addDisplayCategory(null);
        });
    }

    @Test
    public void virtualDisplayConfig_invalidRequestedRefreshRate_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new VirtualDisplayConfig.Builder(NAME, WIDTH, HEIGHT, DENSITY)
                    .setRequestedRefreshRate(-1f);
        });
    }
}
