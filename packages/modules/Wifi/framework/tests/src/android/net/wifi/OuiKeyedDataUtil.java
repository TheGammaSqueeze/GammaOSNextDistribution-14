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

package android.net.wifi;

import android.os.PersistableBundle;

import java.util.ArrayList;
import java.util.List;

/** Test utils for {@link OuiKeyedData} */
public class OuiKeyedDataUtil {
    private static final String STRING_FIELD_KEY = "stringField";
    private static final String ARRAY_FIELD_KEY = "arrayField";

    private static final String STRING_FIELD_VALUE = "someString";
    private static final int[] ARRAY_FIELD_VALUE = new int[] {1, 2, 3};

    /**
     * Generate a single OuiKeyedData object containing several test fields.
     */
    public static OuiKeyedData createTestOuiKeyedData(int oui) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(STRING_FIELD_KEY, STRING_FIELD_VALUE);
        bundle.putIntArray(ARRAY_FIELD_KEY, ARRAY_FIELD_VALUE);
        return new OuiKeyedData.Builder(oui, bundle).build();
    }

    /**
     * Generate a list of OuiKeyedData objects, each containing several test fields.
     */
    public static List<OuiKeyedData> createTestOuiKeyedDataList(int size) {
        List<OuiKeyedData> ouiKeyedDataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ouiKeyedDataList.add(createTestOuiKeyedData(i + 1));
        }
        return ouiKeyedDataList;
    }
}
