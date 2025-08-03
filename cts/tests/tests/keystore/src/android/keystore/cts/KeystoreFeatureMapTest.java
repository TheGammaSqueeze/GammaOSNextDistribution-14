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

package android.keystore.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.hardware.security.keymint.Tag;
import android.security.keymaster.KeymasterDefs;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class KeystoreFeatureMapTest {
    /**
     * Inspect all feature tags defined
     * `hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/Tag.aidl`
     * are mapped to value in
     * `frameworks/base/core/java/android/security/keymaster/KeymasterDefs.java` .
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    @Test
    public void testFeatureTagsMapping()
            throws IllegalArgumentException, IllegalAccessException {
        List<String> skipFields = Arrays.asList("APPLICATION_DATA", "ASSOCIATED_DATA",
                "ATTESTATION_APPLICATION_ID", "BOOTLOADER_ONLY", "EARLY_BOOT_ONLY", "HARDWARE_TYPE",
                "IDENTITY_CREDENTIAL_KEY", "MAX_BOOT_LEVEL", "OS_PATCHLEVEL", "OS_VERSION",
                "ROLLBACK_RESISTANCE", "STORAGE_KEY");
        List<String> missingFields = new ArrayList<>();

        for (Field kmField : Tag.class.getFields()) {
            try {
                Field field = KeymasterDefs.class.getField("KM_TAG_" + kmField.getName());
                assertEquals(kmField.getInt(kmField), field.getInt(field));
            } catch (NoSuchFieldException e) {
                if (!skipFields.contains(kmField.getName())) {
                    missingFields.add(kmField.getName());
                }
            }
        }
        assertTrue("Missing Feature Tags: " + missingFields.toString(), missingFields.isEmpty());
    }
}
