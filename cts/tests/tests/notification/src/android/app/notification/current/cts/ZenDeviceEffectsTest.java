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

package android.app.notification.current.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.ZenDeviceEffects;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_MODES_API)
public class ZenDeviceEffectsTest {

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testEquals() {
        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .setExtraEffects(ImmutableSet.of("One", "Two"))
                .build();
        ZenDeviceEffects same = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .setExtraEffects(ImmutableSet.of("Two", "One"))
                .build();
        ZenDeviceEffects different = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .build();

        assertThat(same).isEqualTo(original);
        assertThat(different).isNotEqualTo(original);
    }

    @Test
    public void testHashcode() {
        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .setExtraEffects(ImmutableSet.of("One", "Two"))
                .build();
        ZenDeviceEffects same = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .setExtraEffects(ImmutableSet.of("Two", "One"))
                .build();
        ZenDeviceEffects different = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .build();

        assertThat(same.hashCode()).isEqualTo(original.hashCode());
        assertThat(different.hashCode()).isNotEqualTo(original.hashCode());
    }

    @Test
    public void testBuilder() {
        ZenDeviceEffects deviceEffects = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldDisplayGrayscale(true)
                .setExtraEffects(ImmutableSet.of("A"))
                .build();

        assertThat(deviceEffects.shouldDimWallpaper()).isTrue();
        assertThat(deviceEffects.shouldDisplayGrayscale()).isTrue();
        assertThat(deviceEffects.shouldSuppressAmbientDisplay()).isFalse();
        assertThat(deviceEffects.shouldUseNightMode()).isFalse();
    }

    @Test
    public void testBuilder_fromInstance() {
        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldUseNightMode(true)
                .setExtraEffects(ImmutableSet.of("B"))
                .build();

        ZenDeviceEffects copy = new ZenDeviceEffects.Builder(original).build();

        assertThat(copy).isEqualTo(original);
    }

    @Test
    public void testBuilder_fromInstance_overwriteFields() {
        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldUseNightMode(true)
                .setExtraEffects(ImmutableSet.of("A", "B"))
                .build();

        ZenDeviceEffects modified = new ZenDeviceEffects.Builder(original)
                .setShouldDisplayGrayscale(true)
                .setShouldUseNightMode(false)
                .setExtraEffects(ImmutableSet.of("C", "D"))
                .build();

        assertThat(modified.shouldDimWallpaper()).isTrue(); // from original
        assertThat(modified.shouldDisplayGrayscale()).isTrue(); // updated
        assertThat(modified.shouldSuppressAmbientDisplay()).isFalse(); // from original
        assertThat(modified.shouldUseNightMode()).isFalse(); // updated
        assertThat(modified.getExtraEffects()).containsExactly("C", "D"); // updated
    }

    @Test
    public void testParceling() {
        ZenDeviceEffects source = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .setExtraEffects(ImmutableSet.of("1", "2", "3"))
                .build();

        Parcel parcel = Parcel.obtain();
        ZenDeviceEffects copy;
        try {
            source.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            copy = ZenDeviceEffects.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }

        assertThat(copy.shouldDimWallpaper()).isTrue();
        assertThat(copy.shouldUseNightMode()).isFalse();
        assertThat(copy.shouldSuppressAmbientDisplay()).isTrue();
        assertThat(copy.shouldDisplayGrayscale()).isFalse();
        assertThat(copy.getExtraEffects()).containsExactly("1", "2", "3");
    }
}
