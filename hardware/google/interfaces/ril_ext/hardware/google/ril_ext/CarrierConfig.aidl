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

package hardware.google.ril_ext;

@VintfStability
parcelable CarrierConfig {
    union ConfigValue {
        boolean boolValue;
        boolean[] boolArray;
        int intValue;
        int[] intArray;
        long longValue;
        long[] longArray;
        String stringValue;
        String[] stringArray;
    }

    /**
     * The name of the carrier config.
     */
    String configKey;
    /**
     * The config value associated with the configKey.
     */
    ConfigValue configValue;
}
