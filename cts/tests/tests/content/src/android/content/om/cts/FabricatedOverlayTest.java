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

package android.content.om.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.cts.R;
import android.content.om.FabricatedOverlay;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.TypedValue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class FabricatedOverlayTest {
    private Context mContext;

    @Rule public TestName mTestName = new TestName();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void newBuilder_withGoodOverlayName_shouldSucceed() {
        new FabricatedOverlay("I_am_good_name", mContext.getPackageName());
    }

    @Test
    public void newBuilder_withNullOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () -> new FabricatedOverlay(null /* overlayName */, mContext.getPackageName()));
    }

    @Test
    public void newBuilder_withEmptyOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () -> new FabricatedOverlay("" /* overlayName */, mContext.getPackageName()));
    }

    @Test
    public void newBuilder_withBadOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () ->
                        new FabricatedOverlay(
                                "../../etc/password", mContext.getPackageName()));
    }

    @Test
    public void setResourceValue_forResourceName_withoutSlash_shouldBeInvalid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        overlay.setResourceValue("demo", TypedValue.TYPE_INT_COLOR_ARGB8,
                                Color.WHITE, null /* configuration */));
    }

    @Test
    public void setResourceValue_forResourceName_colonAfterSlash_shouldBeInvalid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        overlay.setResourceValue(
                                "color/" + mContext.getPackageName() + " :demo",
                                TypedValue.TYPE_INT_COLOR_ARGB8,
                                Color.WHITE, null /* configuration */));
    }

    @Test
    public void setResourceValue_forResourceName_invalidResourceType_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        overlay.setResourceValue("s/demo", TypedValue.TYPE_INT_COLOR_ARGB8,
                                Color.WHITE, null /* configuration */));
    }

    @Test
    public void setResourceValue_forResourceName_colonBeforeSlash_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue(mContext.getPackageName() + ":color/demo",
                TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE, null /* configuration */);
    }

    @Test
    public void setResourceValue_forIntType_colorAsStringType_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () -> overlay.setResourceValue("color/demo", TypedValue.TYPE_STRING, Color.WHITE,
                        null /* configuration */));
    }

    @Test
    public void setResourceValue_forIntType_forColorType_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue("color/demo", TypedValue.TYPE_INT_COLOR_ARGB4, Color.WHITE,
                null /* configuration */);
    }

    @Test
    public void setResourceValue_forIntType_withConfigurations_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue(
                "color/demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE, "port");
        overlay.setResourceValue(
                "color/demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE, "land");
    }

    @Test
    public void setResourceValue_forIntType_forNotExistColor_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue("color/want ../../etc/password", TypedValue.TYPE_INT_COLOR_ARGB8,
                Color.WHITE, null /* configuration */);
    }

    @Test
    public void setResourceValue_forStringType_forNotExistString_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue(
                "string/want ../../etc/password",
                TypedValue.TYPE_STRING,
                "Try to replace non-exist string",
                null /* configuration */);
    }

    @Test
    public void setResourceValue_forStringType_withConfigurations_shouldBeValid() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setResourceValue(
                "string/demo", TypedValue.TYPE_STRING, "I am string for port", "port");
        overlay.setResourceValue(
                "string/demo", TypedValue.TYPE_STRING, "I am string for land", "land");
    }

    @Test
    public void setResourceValue_forStringType_nullString_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                NullPointerException.class,
                () -> overlay.setResourceValue("string/demo", TypedValue.TYPE_STRING, null,
                        null /* configuration */));
    }

    @Test
    public void setResourceValue_forStringType_stringAsColorType_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        overlay.setResourceValue("string/demo", TypedValue.TYPE_INT_COLOR_ARGB8,
                                "Hello", null /* configuration */));
    }

    @Test
    public void setResourceValue_forParcelFileDescriptor_withNull_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                NullPointerException.class,
                () ->
                        overlay.setResourceValue(
                                "layout/demo",
                                (ParcelFileDescriptor) null /* value */,
                                null /* configuration */));
    }

    @Test
    public void setNinePatchResourceValue_withNull_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                NullPointerException.class,
                () ->
                        overlay.setNinePatchResourceValue(
                                "layout/demo",
                                null /* value */,
                                null /* configuration */));
    }

    @Test
    public void setResourceValue_forAssetFileDescriptor_withNull_shouldFail() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThrows(
                NullPointerException.class,
                () ->
                        overlay.setResourceValue(
                                "layout/demo",
                                (AssetFileDescriptor) null /* value */,
                                null /* configuration */));
    }

    @Test
    public void setResourceValue_multipleEntries_shouldSucceed() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());
        final ParcelFileDescriptor parcelFileDescriptor =
                mContext.getResources().openRawResourceFd(R.raw.text).getParcelFileDescriptor();
        AssetFileDescriptor assetFileDescriptor = new AssetFileDescriptor(
                parcelFileDescriptor, 0, parcelFileDescriptor.getStatSize());

        overlay.setResourceValue("color/demo1", TypedValue.TYPE_INT_COLOR_ARGB4, Color.WHITE,
                null /* configuration */);
        overlay.setResourceValue("color/demo2", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE,
                null /* configuration */);
        overlay.setResourceValue("string/demo1", TypedValue.TYPE_STRING, "white",
                null /* configuration */);
        overlay.setResourceValue("string/demo2", TypedValue.TYPE_STRING, "black",
                null /* configuration */);
        overlay.setResourceValue("raw/demo", parcelFileDescriptor, null /* configuration */);
        overlay.setNinePatchResourceValue(
                "raw/demo", parcelFileDescriptor, null /* configuration */);
        overlay.setResourceValue("raw/demo2", assetFileDescriptor, null /* configuration */);
    }

    @Test
    public void getTargetOverlayable_defaultIsNull() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThat(overlay.getTargetOverlayable()).isEmpty();
    }

    @Test
    public void getTargetOverlayable_setTargetOverlayable_shouldBeTheSame() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        overlay.setTargetOverlayable("Hello");

        assertThat(overlay.getTargetOverlayable()).isEqualTo("Hello");
    }

    @Test
    public void getOverlayIdentifier_defaultIsNotNull() {
        final FabricatedOverlay overlay =
                new FabricatedOverlay(mTestName.getMethodName(), mContext.getPackageName());

        assertThat(overlay.getIdentifier()).isNotNull();
    }
}
