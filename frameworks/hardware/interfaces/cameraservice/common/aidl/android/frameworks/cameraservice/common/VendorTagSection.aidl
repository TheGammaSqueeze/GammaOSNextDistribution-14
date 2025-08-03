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

package android.frameworks.cameraservice.common;

import android.frameworks.cameraservice.common.VendorTag;

/**
 * A set of related vendor tags.
 */
@VintfStability
parcelable VendorTagSection {
    /**
     * Section name; must be namespaced within vendor's name.
     */
    String sectionName;
    /**
     * List of tags in this section
     */
    VendorTag[] tags;
}
