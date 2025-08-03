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

package android.healthconnect.internal.datatypes;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.os.Parcel;

import org.junit.Test;

public class ExerciseRouteInternalTest {

    @Test
    public void testLocation_convertToExternalAndBack_isIdentical() {
        ExerciseRouteInternal.LocationInternal location =
                TestUtils.buildInternalLocationAllFields();
        ExerciseRouteInternal.LocationInternal convertedLocation =
                location.toExternalExerciseRouteLocation().toExerciseRouteLocationInternal();
        assertThat(convertedLocation).isEqualTo(location);
    }

    @Test
    public void testLocationNoOptionalFields_convertToExternalAndBack_isIdentical() {
        ExerciseRouteInternal.LocationInternal location = TestUtils.buildInternalLocation();
        ExerciseRouteInternal.LocationInternal convertedLocation =
                location.toExternalExerciseRouteLocation().toExerciseRouteLocationInternal();
        assertThat(convertedLocation).isEqualTo(location);
    }

    @Test
    public void testRouteConvertToExternal_convertToExternalAndBack_isIdentical() {
        ExerciseRouteInternal mRoute = TestUtils.buildExerciseRouteInternal();
        ExerciseRouteInternal convertedRoute = mRoute.toExternalRoute().toRouteInternal();
        assertThat(convertedRoute).isEqualTo(mRoute);
    }

    @Test
    public void testRouteWriteToParcel_writeReadFromParcel_isIdentical() {
        ExerciseRouteInternal mRoute = TestUtils.buildExerciseRouteInternal();
        Parcel parcel = Parcel.obtain();
        ExerciseRouteInternal.writeToParcel(mRoute, parcel);
        parcel.setDataPosition(0);
        ExerciseRouteInternal restoredRoute = ExerciseRouteInternal.readFromParcel(parcel);
        assertThat(restoredRoute.getRouteLocations()).isEqualTo(mRoute.getRouteLocations());
        assertThat(restoredRoute).isEqualTo(mRoute);
    }

    @Test
    public void testRouteWriteToParcel_routeIsNull_isIdentical() {
        ExerciseRouteInternal mRoute = null;
        Parcel parcel = Parcel.obtain();
        ExerciseRouteInternal.writeToParcel(mRoute, parcel);
        parcel.setDataPosition(0);
        ExerciseRouteInternal restoredRoute = ExerciseRouteInternal.readFromParcel(parcel);
        assertThat(restoredRoute).isEqualTo(mRoute);
    }
}
