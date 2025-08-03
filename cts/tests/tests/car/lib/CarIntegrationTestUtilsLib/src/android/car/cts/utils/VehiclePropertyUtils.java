/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts.utils;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class VehiclePropertyUtils {

    private VehiclePropertyUtils() {
    }

    /** Returns all integer type enums from the class */
    public static List<Integer> getIntegersFromDataEnums(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<Integer> integerList = new ArrayList<>(5);
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    integerList.add(f.getInt(clazz));
                } catch (IllegalAccessException | RuntimeException e) {
                    Log.w(clazz.getSimpleName(), "Failed to get value");
                }
            }
        }
        return integerList;
    }
}
