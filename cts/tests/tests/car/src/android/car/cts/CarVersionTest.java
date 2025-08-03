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
package android.car.cts;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.CarVersion;
import android.car.annotation.ApiRequirements;
import android.os.Parcel;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

public final class CarVersionTest extends AbstractCarLessTestCase {

    @Test
    @ApiTest(apis = {"android.car.CarVersion.VERSION_CODES#TIRAMISU_0"})
    public void testTiramisu_0() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_0;

        assertWithMessage("TIRAMISU_0").that(version).isNotNull();
        expectWithMessage("TIRAMISU_0.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_0.minor").that(version.getMinorVersion())
                .isEqualTo(0);

        // Check against other versions
        expectWithMessage("isAtLeast(TM_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_0))
                .isTrue();
        expectWithMessage("isAtLeast(TM_1)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1))
                .isFalse();
        expectWithMessage("isAtLeast(TM_2)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_2))
                .isFalse();
        expectWithMessage("isAtLeast(TM_3)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_3))
                .isFalse();
        expectWithMessage("isAtLeast(UDC_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0))
                .isFalse();

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_0.get();
        assertWithMessage("TIRAMISU_0 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_0 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_0.toString()").that(toString)
                .matches(".*CarVersion.*name=TIRAMISU_0.*major=" + TIRAMISU + ".*minor=0.*");
        CarVersion clone = clone(version);
        expectWithMessage("TIRAMISU_0.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        CarVersion anonymous = CarVersion.forMajorAndMinorVersions(version.getMajorVersion(),
                version.getMinorVersion());
        expectWithMessage("TIRAMISU_0").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_0's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion.VERSION_CODES#TIRAMISU_1"})
    public void testTiramisu_1() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_1;

        assertWithMessage("TIRAMISU_1").that(version).isNotNull();
        expectWithMessage("TIRAMISU_1.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_1.minor").that(version.getMinorVersion())
                .isEqualTo(1);

        // Check against other versions
        expectWithMessage("isAtLeast(TM_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_0))
                .isTrue();
        expectWithMessage("isAtLeast(TM_1)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1))
                .isTrue();
        expectWithMessage("isAtLeast(TM_2)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_2))
                .isFalse();
        expectWithMessage("isAtLeast(TM_3)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_3))
                .isFalse();
        expectWithMessage("isAtLeast(UDC_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0))
                .isFalse();

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_1.get();
        assertWithMessage("TIRAMISU_1 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_1 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_1.toString()").that(toString)
                .matches(".*CarVersion.*name=TIRAMISU_1.*major=" + TIRAMISU + ".*minor=1.*");
        CarVersion clone = clone(version);
        expectWithMessage("TIRAMISU_1.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        CarVersion anonymous = CarVersion.forMajorAndMinorVersions(version.getMajorVersion(),
                version.getMinorVersion());
        expectWithMessage("TIRAMISU_1").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_1's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion.VERSION_CODES#TIRAMISU_2"})
    public void testTiramisu_2() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_2;

        assertWithMessage("TIRAMISU_2").that(version).isNotNull();
        expectWithMessage("TIRAMISU_2.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_2.minor").that(version.getMinorVersion())
                .isEqualTo(2);

        // Check against other versions
        expectWithMessage("isAtLeast(TM_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_0))
                .isTrue();
        expectWithMessage("isAtLeast(TM_1)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1))
                .isTrue();
        expectWithMessage("isAtLeast(TM_2)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_2))
                .isTrue();
        expectWithMessage("isAtLeast(TM_3)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_3))
                .isFalse();
        expectWithMessage("isAtLeast(UDC_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0))
                .isFalse();

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_2.get();
        assertWithMessage("TIRAMISU_2 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_2 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_2.toString()").that(toString)
                .matches(".*CarVersion.*name=TIRAMISU_2.*major=" + TIRAMISU + ".*minor=2.*");
        CarVersion clone = clone(version);
        expectWithMessage("TIRAMISU_2.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        CarVersion anonymous = CarVersion.forMajorAndMinorVersions(version.getMajorVersion(),
                version.getMinorVersion());
        expectWithMessage("TIRAMISU_2").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_2's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion.VERSION_CODES#TIRAMISU_3"})
    public void testTiramisu_3() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_3;

        assertWithMessage("TIRAMISU_3").that(version).isNotNull();
        expectWithMessage("TIRAMISU_3.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_3.minor").that(version.getMinorVersion())
                .isEqualTo(3);

        // Check against other versions
        expectWithMessage("isAtLeast(TM_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_0))
                .isTrue();
        expectWithMessage("isAtLeast(TM_1)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1))
                .isTrue();
        expectWithMessage("isAtLeast(TM_2)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_2))
                .isTrue();
        expectWithMessage("isAtLeast(TM_3)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_3))
                .isTrue();
        expectWithMessage("isAtLeast(UDC_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0))
                .isFalse();

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_3.get();
        assertWithMessage("TIRAMISU_3 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_3 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_3.toString()").that(toString)
                .matches(".*CarVersion.*name=TIRAMISU_3.*major=" + TIRAMISU + ".*minor=3.*");
        CarVersion clone = clone(version);
        expectWithMessage("TIRAMISU_3.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        CarVersion anonymous = CarVersion.forMajorAndMinorVersions(version.getMajorVersion(),
                version.getMinorVersion());
        expectWithMessage("TIRAMISU_3").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_3's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion.VERSION_CODES#UPSIDE_DOWN_CAKE_0"})
    public void testUpsideDownCake_0() {
        CarVersion version = CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;

        assertWithMessage("UPSIDE_DOWN_CAKE_0").that(version).isNotNull();
        expectWithMessage("UPSIDE_DOWN_CAKE_0.major").that(version.getMajorVersion())
                .isEqualTo(UPSIDE_DOWN_CAKE);
        expectWithMessage("UPSIDE_DOWN_CAKE_0.minor").that(version.getMinorVersion())
                .isEqualTo(0);

        // Check against other versions
        expectWithMessage("isAtLeast(TM_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_0))
                .isTrue();
        expectWithMessage("isAtLeast(TM_1)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1))
                .isTrue();
        expectWithMessage("isAtLeast(TM_2)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_2))
                .isTrue();
        expectWithMessage("isAtLeast(TM_3)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_3))
                .isTrue();
        expectWithMessage("isAtLeast(UDC_0)")
                .that(version.isAtLeast(CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0))
                .isTrue();

        CarVersion fromEnum = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0.get();
        assertWithMessage("UPSIDE_DOWN_CAKE_0 from enum").that(fromEnum).isNotNull();
        expectWithMessage("UPSIDE_DOWN_CAKE_0 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("UPSIDE_DOWN_CAKE_0.toString()").that(toString)
                .matches(".*CarVersion.*name=UPSIDE_DOWN_CAKE_0.*major=" + UPSIDE_DOWN_CAKE
                        + ".*minor=0.*");
        CarVersion clone = clone(version);
        expectWithMessage("UPSIDE_DOWN_CAKE_0.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        CarVersion anonymous = CarVersion.forMajorAndMinorVersions(version.getMajorVersion(),
                version.getMinorVersion());
        expectWithMessage("UPSIDE_DOWN_CAKE_0").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("UPSIDE_DOWN_CAKE_0's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion#CREATOR"})
    public void testMarshalling() {
        CarVersion original = CarVersion.forMajorAndMinorVersions(66, 6);
        expectWithMessage("original.describeContents()").that(original.describeContents())
                .isEqualTo(0);

        CarVersion clone = clone(original);
        assertWithMessage("CREATOR.createFromParcel()").that(clone).isNotNull();
        expectWithMessage("clone.major").that(clone.getMajorVersion()).isEqualTo(66);
        expectWithMessage("clone.minor").that(clone.getMinorVersion()).isEqualTo(6);
        expectWithMessage("clone.describeContents()").that(clone.describeContents()).isEqualTo(0);
        expectWithMessage("clone.equals()").that(clone).isEqualTo(original);
        expectWithMessage("clone.hashCode()").that(clone.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.CarVersion#CREATOR"})
    public void testNewArray() {
        CarVersion[] array = CarVersion.CREATOR.newArray(42);

        assertWithMessage("CREATOR.newArray()").that(array).isNotNull();
        expectWithMessage("CREATOR.newArray()").that(array).hasLength(42);
    }

    private CarVersion clone(CarVersion original) {
        Parcel parcel =  Parcel.obtain();
        try {
            original.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);

            return CarVersion.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
