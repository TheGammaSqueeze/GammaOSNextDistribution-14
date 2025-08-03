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

package android.hardware.input.cts.tests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.flags.Flags;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualStylusConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualInputDeviceConfigTest {

    private static final int PRODUCT_ID = 1;
    private static final int VENDOR_ID = 1;
    private static final String DEVICE_NAME = "VirtualTestDevice";
    private static final String DEVICE_NAME_THAT_IS_TOO_LONG =
            "The way to dusty death. Out, out, brief candle." +
            "Life’s but a walking shadow, a poor player";

    // Has only 39 characters but is 109 bytes as utf-8
    private static final String UTF8_DEVICE_NAME_THAT_IS_TOO_LONG =
            "░▄▄▄▄░\n" +
            "▀▀▄██►\n" +
            "▀▀███►\n" +
            "░▀███►░█►\n" +
            "▒▄████▀▀";

    private static final int DISPLAY_ID = 2;
    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private VirtualDpadConfig createVirtualDpadConfig() {
        return new VirtualDpadConfig.Builder()
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
    }

    private VirtualKeyboardConfig createVirtualKeyboardConfigWithLanguageTag(String languageTag) {
        return new VirtualKeyboardConfig.Builder()
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setLanguageTag(languageTag)
                .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                .build();
    }

    private VirtualKeyboardConfig createVirtualKeyboardConfig() {
        return createVirtualKeyboardConfigWithLanguageTag(
                VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG);
    }

    private VirtualMouseConfig createVirtualMouseConfig() {
        return new VirtualMouseConfig.Builder()
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
    }

    private VirtualTouchscreenConfig createVirtualTouchscreenConfig() {
        return new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
    }

    private VirtualStylusConfig createVirtualStylusConfig() {
        return new VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
    }

    private VirtualNavigationTouchpadConfig createVirtualNavigationTouchpadConfig() {
        return new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
    }

    @Test
    public void testConstructorAndGetters_virtualDpadConfig() {
        VirtualDpadConfig config = createVirtualDpadConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void testParcel_virtualDpadConfig() {
        VirtualDpadConfig config = createVirtualDpadConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualDpadConfig configFromParcel = VirtualDpadConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
    }

    @Test
    public void testConstructorAndGetters_virtualKeyboardConfig() {
        VirtualKeyboardConfig config = createVirtualKeyboardConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
        assertThat(config.getLanguageTag()).isEqualTo(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG);
        assertThat(config.getLayoutType()).isEqualTo(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE);
    }

    @Test
    public void testParcel_virtualKeyboardConfig() {
        VirtualKeyboardConfig config = createVirtualKeyboardConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualKeyboardConfig configFromParcel =
                VirtualKeyboardConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
        assertThat(configFromParcel.getLanguageTag()).isEqualTo(
                config.getLanguageTag());
        assertThat(configFromParcel.getLayoutType()).isEqualTo(
                config.getLayoutType());
    }

    @Test
    public void testBuilder_virtualKeyboardConfig_defaultValuedUsed() {
        //(TODO:b/262924887) Add end-to-end tests for selecting virtual keyboard layout.
        VirtualKeyboardConfig config = new VirtualKeyboardConfig.Builder()
                .setInputDeviceName(DEVICE_NAME)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build();
        assertThat(config.getLanguageTag()).isEqualTo(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG);
        assertThat(config.getLayoutType()).isEqualTo(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE);
    }

    @Test
    public void testBuilder_wellFormedTags_noException() {
        VirtualKeyboardConfig.Builder builder = new VirtualKeyboardConfig.Builder();

        String wellFormedTag1 = "ru-Cyrl";
        builder.setLanguageTag(wellFormedTag1);

        String wellFormedTag2 = "es-Latn-419";
        builder.setLanguageTag(wellFormedTag2);

        String wellFormedTag3 = "zh-CN";
        builder.setLanguageTag(wellFormedTag3);

        String wellFormedTag4 = "pt-Latn-PT";
        builder.setLanguageTag(wellFormedTag4);
    }

    @Test
    public void testConstructorAndGetters_virtualMouseConfig() {
        VirtualMouseConfig config = createVirtualMouseConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void testParcel_virtualMouseConfig() {
        VirtualMouseConfig config = createVirtualMouseConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualMouseConfig configFromParcel = VirtualMouseConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
    }

    @Test
    public void testConstructorAndGetters_virtualTouchscreenConfig() {
        VirtualTouchscreenConfig config = createVirtualTouchscreenConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
        assertThat(config.getWidth()).isEqualTo(WIDTH);
        assertThat(config.getHeight()).isEqualTo(HEIGHT);
    }

    @Test
    public void testParcel_virtualTouchscreenConfig() {
        VirtualTouchscreenConfig config = createVirtualTouchscreenConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualTouchscreenConfig configFromParcel =
                VirtualTouchscreenConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
        assertThat(configFromParcel.getWidth()).isEqualTo(config.getWidth());
        assertThat(configFromParcel.getHeight()).isEqualTo(config.getHeight());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void testConstructorAndGetters_virtualStylusConfig() {
        VirtualStylusConfig config = createVirtualStylusConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
        assertThat(config.getWidth()).isEqualTo(WIDTH);
        assertThat(config.getHeight()).isEqualTo(HEIGHT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void testParcel_virtualStylusConfig() {
        VirtualStylusConfig config = createVirtualStylusConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualStylusConfig configFromParcel =
                VirtualStylusConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
        assertThat(configFromParcel.getWidth()).isEqualTo(config.getWidth());
        assertThat(configFromParcel.getHeight()).isEqualTo(config.getHeight());
    }

    @Test
    public void testConstructorAndGetters_virtualNavigationTouchpadConfig() {
        VirtualNavigationTouchpadConfig config = createVirtualNavigationTouchpadConfig();
        assertThat(config.getInputDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(config.getVendorId()).isEqualTo(VENDOR_ID);
        assertThat(config.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(config.getAssociatedDisplayId()).isEqualTo(DISPLAY_ID);
        assertThat(config.getWidth()).isEqualTo(WIDTH);
        assertThat(config.getHeight()).isEqualTo(HEIGHT);
    }

    @Test
    public void testParcel_virtualNavigationTouchpadConfig() {
        VirtualNavigationTouchpadConfig config = createVirtualNavigationTouchpadConfig();
        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualNavigationTouchpadConfig configFromParcel =
                VirtualNavigationTouchpadConfig.CREATOR.createFromParcel(parcel);
        assertThat(configFromParcel.getInputDeviceName()).isEqualTo(config.getInputDeviceName());
        assertThat(configFromParcel.getVendorId()).isEqualTo(config.getVendorId());
        assertThat(configFromParcel.getProductId()).isEqualTo(config.getProductId());
        assertThat(configFromParcel.getAssociatedDisplayId()).isEqualTo(
                config.getAssociatedDisplayId());
        assertThat(configFromParcel.getWidth()).isEqualTo(config.getWidth());
        assertThat(configFromParcel.getHeight()).isEqualTo(config.getHeight());
    }

    @Test
    public void virtualDpadConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualDpadConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @Test
    public void virtualDpadConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualDpadConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualDpadConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @Test
    public void virtualDpadConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @Test
    public void virtualKeyboardConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @Test
    public void virtualKeyboardConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @Test
    public void virtualKeyboardConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @Test
    public void virtualMouseConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @Test
    public void virtualMouseConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @Test
    public void virtualMouseConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @Test
    public void virtualNavigationTouchpadConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @Test
    public void virtualNavigationTouchpadConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @Test
    public void virtualNavigationTouchpadConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @Test
    public void virtualNavigationTouchpadConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(WIDTH, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(0, HEIGHT));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualNavigationTouchpadConfig.Builder(0, 0));
    }

    @Test
    public void virtualTouchscreenConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @Test
    public void virtualTouchscreenConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @Test
    public void virtualTouchscreenConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @Test
    public void virtualTouchscreenConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(WIDTH, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(0, HEIGHT));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualTouchscreenConfig.Builder(0, 0));
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void virtualStylusConfig_missingName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void virtualStylusConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setAssociatedDisplayId(DISPLAY_ID)
                        .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                        .build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void virtualStylusConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
    @Test
    public void virtualStylusConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(WIDTH, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(0, HEIGHT));
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualStylusConfig.Builder(0, 0));
    }
}
