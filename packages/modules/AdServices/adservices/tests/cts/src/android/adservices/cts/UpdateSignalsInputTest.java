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

package android.adservices.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link UpdateSignalsInput}
 *
 * <p>If this class is un-ignored {@link android.adservices.signals.UpdateSignalsInputTest} should
 * be deleted.
 */
@Ignore
public class UpdateSignalsInputTest {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String OTHER_PACKAGE = CommonFixture.TEST_PACKAGE_NAME_2;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuild() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertEquals(URI, input.getUpdateUri());
        assertEquals(PACKAGE, input.getCallerPackageName());
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new UpdateSignalsInput.Builder(null, PACKAGE).build());
    }

    @Test
    public void testBuildNullPackage_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new UpdateSignalsInput.Builder(URI, null).build());
    }

    @Test
    public void testParceling() {
        UpdateSignalsInput expected = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        Parcel parcel = Parcel.obtain();

        expected.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UpdateSignalsInput actual = UpdateSignalsInput.CREATOR.createFromParcel(parcel);

        assertEquals(expected, actual);
    }

    @Test
    public void testUnParcelNullUri() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, null);
        parcel.writeString(PACKAGE);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> UpdateSignalsInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testUnParcelNullPackage() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, URI);
        parcel.writeString(null);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> UpdateSignalsInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testNewArray() {
        int arrayLength = 5;
        UpdateSignalsInput[] array = UpdateSignalsInput.CREATOR.newArray(arrayLength);
        assertEquals(arrayLength, array.length);
    }

    @Test
    public void testDescribeContents() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertEquals(0, input.describeContents());
    }

    @Test
    public void testEqualsEqual() {
        UpdateSignalsInput identical1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        UpdateSignalsInput identical2 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertEquals(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        UpdateSignalsInput different1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        UpdateSignalsInput different2 = new UpdateSignalsInput.Builder(URI, OTHER_PACKAGE).build();
        assertNotEquals(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        UpdateSignalsInput input1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertNotEquals(input1, new Object());
    }

    @Test
    public void testHash() {
        UpdateSignalsInput identical1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        UpdateSignalsInput identical2 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertEquals(identical1.hashCode(), identical2.hashCode());
    }

    @Test
    public void testToString() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertEquals(
                "UpdateSignalsInput{"
                        + "mUpdateUri="
                        + URI
                        + ", mCallerPackageName='"
                        + PACKAGE
                        + '\''
                        + '}',
                input.toString());
    }
}
