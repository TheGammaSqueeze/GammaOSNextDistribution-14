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

package healthconnect.storage.utils;

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;

import android.health.connect.datatypes.RecordTypeIdentifier;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.storage.utils.RecordTypeForUuidMappings;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class RecordTypeForUuidMappingsTest {

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void testForEveryInternalRecordTypeReturnsDistinctResult_exceptWipDataTypes() {
        final Set<Integer> resultTypeIds = new HashSet<>();
        for (Integer recordTypeId : RecordTypeIdentifier.VALID_TYPES) {
            if (recordTypeId == RECORD_TYPE_SKIN_TEMPERATURE
                    || recordTypeId == RECORD_TYPE_PLANNED_EXERCISE_SESSION) {
                return;
            }
            final int resultTypeId = RecordTypeForUuidMappings.getRecordTypeIdForUuid(recordTypeId);
            mExpect.that(resultTypeIds).doesNotContain(resultTypeId);
            resultTypeIds.add(resultTypeId);
        }
    }
}
