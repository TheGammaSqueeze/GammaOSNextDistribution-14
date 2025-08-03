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

package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.PackageInstaller.PreapprovalDetails;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.icu.util.ULocale;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BitmapUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link PreapprovalDetails}
 */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class PreapprovalDetailsTest {
    private static final Bitmap TEST_APP_ICON = Bitmap.createBitmap(42, 42, Config.ARGB_8888);
    private static final String TEST_APP_LABEL = "APP NAME";
    // Create a mock ULocale object with the given language, script, country, and variant.
    private static final ULocale TEST_LOCALE = new ULocale("aa_bbbb_cc_dd");
    private static final String TEST_PACKAGE_NAME = "com.foo";

    @Test
    public void testPreapprovalDetails() {
        // Test PreapprovalDetails.Builder, getter
        final PreapprovalDetails details = new PreapprovalDetails.Builder()
                .setIcon(TEST_APP_ICON)
                .setLabel(TEST_APP_LABEL)
                .setLocale(TEST_LOCALE)
                .setPackageName(TEST_PACKAGE_NAME)
                .build();
        assertThat(BitmapUtils.compareBitmaps(details.getIcon(), TEST_APP_ICON)).isTrue();
        assertThat(details.getLabel()).isEqualTo(TEST_APP_LABEL);
        assertThat(details.getLocale()).isEqualTo(TEST_LOCALE);
        assertThat(details.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);

        // Test toString, describeContents
        assertThat(details.toString()).isNotNull();
        assertThat(details.describeContents()).isEqualTo(0);

        // Test writeToParcel, PreapprovalDetails(Parcel source)
        final Parcel p = Parcel.obtain();
        details.writeToParcel(p, 0 /* flags */);
        p.setDataPosition(0);
        // CREATOR invokes public PackageStats(Parcel source)
        final PreapprovalDetails detailsParcel = PreapprovalDetails.CREATOR.createFromParcel(p);
        checkTheSame(details, detailsParcel);
        p.recycle();
    }

    @Test
    public void testPreapprovalDetails_parcelWithNullAppIcon() {
        final PreapprovalDetails details = new PreapprovalDetails.Builder()
                .setLabel(TEST_APP_LABEL)
                .setLocale(TEST_LOCALE)
                .setPackageName(TEST_PACKAGE_NAME)
                .build();

        final Parcel p = Parcel.obtain();
        details.writeToParcel(p, 0 /* flags */);
        p.setDataPosition(0);
        final PreapprovalDetails detailsParcel = PreapprovalDetails.CREATOR.createFromParcel(p);
        checkTheSame(details, detailsParcel);
        p.recycle();
    }

    private void checkTheSame(PreapprovalDetails expected, PreapprovalDetails actual) {
        assertThat(BitmapUtils.compareBitmaps(actual.getIcon(), expected.getIcon())).isTrue();
        assertThat(actual.getLabel()).isEqualTo(expected.getLabel());
        assertThat(actual.getLocale()).isEqualTo(expected.getLocale());
        assertThat(actual.getPackageName()).isEqualTo(expected.getPackageName());
    }
}
