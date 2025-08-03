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

package android.healthconnect;

import static com.google.common.truth.Truth.assertWithMessage;

import android.health.connect.datatypes.ExerciseSegmentType;
import android.util.ArraySet;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExerciseSegmentTypesTest {

    @Test
    public void testExerciseSegmentTypeStrings_allValuesAreUnique() throws IllegalAccessException {
        List<Integer> types = getAllDeclaredExerciseSegmentTypes();
        Set<Integer> uniqueTypes = new ArraySet<>(types);
        assertWithMessage("All values of segment types must be unique.")
                .that(types.size())
                .isEqualTo(uniqueTypes.size());
    }

    private List<Integer> getAllDeclaredExerciseSegmentTypes() throws IllegalAccessException {
        List<Integer> types = new ArrayList<>();
        for (Field field : ExerciseSegmentType.class.getDeclaredFields()) {
            if (field.getType().equals(int.class) && Modifier.isPublic(field.getModifiers())) {
                types.add((Integer) field.get(ExerciseSegmentType.class));
            }
        }
        return types;
    }
}
