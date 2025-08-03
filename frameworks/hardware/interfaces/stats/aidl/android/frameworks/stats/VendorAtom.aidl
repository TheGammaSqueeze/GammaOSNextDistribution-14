//
// Copyright (C) 2021 The Android Open Source Project
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
import android.frameworks.stats.AnnotationSet;
import android.frameworks.stats.VendorAtomValue;

/*
 * Generic vendor atom that allows dynamically allocated atoms to be uploaded
 * through statsd.
 *
 * Here's an example that uses this struct:
 *     VendorAtom atom = {
 *         .atomId  = 100000,
 *         .values  = {2, 70000, 5.2, 4, "a"}
 *     };
 *
 * The resulting LogEvent must have the following fields:
 *     Index    Value
 *     0x1      2
 *     0x2      70000
 *     0x3      5.2
 *     0x4      4
 *     0x5      "a"
 */
@VintfStability
parcelable VendorAtom {
    /**
     * Vendor or OEM reverse domain name. Must be less than 50 characters.
     * Ex. "com.google.pixel"
     */
    String reverseDomainName;
    /*
     * Atom ID. Must be between 100,000 - 199,999 to indicate non-AOSP field.
     */
    int atomId;
    /*
     * Vector of fields in the order that the LogEvent should be filled.
     */
    VendorAtomValue[] values;

    /*
    * Vector of annotations associated with VendorAtom.values
    *
    * Having the atom with below definition
    *
    * message SimpleVendorAtom {
    *   optional string reverse_domain_name = 1;
    *   optional int field1 = 2 [annotation1 = 1, annotation2 = true];
    *   optional float field2 = 3;
    *   optional float field3 = 4 [annotation1 = 2, annotation2 = false];
    * }
    *
    * The valuesAnnotations will contain 2 entries
    *  - valuesAnnotations[0] for field1
    *  - valuesAnnotations[1] for field3
    *
    * The VendorAtomAnnotationSet[i].valueIndex used for mapping each individual
    * annotation set to specific atom value by VendorAtom.values array index:
    *
    * valuesAnnotations[0].valueIndex = 0 // index of field1 in VendorAtom.values[] array
    * valuesAnnotations[0].annotations[0].type = annotation1
    * valuesAnnotations[0].annotations[0].value = 1
    * valuesAnnotations[0].annotations[1].type = annotation2
    * valuesAnnotations[0].annotations[1].value = true
    *
    * valuesAnnotations[1].valueIndex = 2 // index of field1 in VendorAtom.values[] array
    * valuesAnnotations[1].annotations[0].type = annotation1
    * valuesAnnotations[1].annotations[0].value = 2
    * valuesAnnotations[1].annotations[1].type = annotation2
    * valuesAnnotations[1].annotations[1].value = false
    */
    @nullable AnnotationSet[] valuesAnnotations;

    /*
    * Vector of annotations associated with VendorAtom
    */
    @nullable Annotation[] atomAnnotations;
}
