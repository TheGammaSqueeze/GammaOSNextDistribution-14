//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package android.frameworks.stats;

import android.frameworks.stats.Annotation;

/*
 * Single VendorAtomValue could have multiple annotations associated with it
 */
@VintfStability
parcelable AnnotationSet {
    /*
     * Index to map VendorAtomValueAnnotation array with an individual VendorAtomValue
     * from VendorAtom.values
     */
    int valueIndex;
    /*
     * Vector of annotations associated with VendorAtom.values[valueIndex]
     */
    Annotation[] annotations;
}
