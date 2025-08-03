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
@Backing(type="int")
@JavaDerive(toString=true)
enum RilError {
    /**
     * Success
     */
    NONE = 0,
    GENERIC_FAILURE = 1,
    /**
     * Optional API
     */
    REQUEST_NOT_SUPPORTED = 2,
    /**
     * Not sufficieent memory to process the request
     */
    NO_MEMORY = 3,
}
