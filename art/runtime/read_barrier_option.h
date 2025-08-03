/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_READ_BARRIER_OPTION_H_
#define ART_RUNTIME_READ_BARRIER_OPTION_H_

#include "base/macros.h"

namespace art HIDDEN {

// Options for performing a read barrier or not.
//
// Besides disabled GC and GC's internal usage, there are a few cases where the read
// barrier is unnecessary and can be avoided to reduce code size and improve performance.
// In the following cases, the result of the operation or chain of operations shall be the
// same whether we go through the from-space or to-space:
//
// 1. We're reading a reference known to point to an un-reclaimable immune space object.
//    (For example boot image class and string references, read by compiled code from
//    .data.bimg.rel.ro . Similarly, such references constructed using position independent
//    code in the compiled boot image code do not need a read barrier.)
// 2. We're reading the reference for comparison involving a non-moving space reference.
//    (Whether the non-moving space reference is the one we're reading or the one we shall
//    compare it with, the result is the same with and without read barrier.)
// 3. We're reading the reference for comparison with null.
//    (Similar to 2 above, given that null is "non-moving".)
// 4. We're reading a reference to a holder from which we shall read
//      - constant primitive field, or
//      - mutable primitive field for testing an invariant, or
//      - constant reference field known to point to an un-reclaimable immune space object, or
//      - constant reference field for comparison involving a non-moving space reference, or
//      - constant reference field for comparison with null, or
//      - constant reference fields in a chain leading to one or more of the above purposes;
//        the entire chain needs to be read without read barrier.
// The terms "constant" and "invariant" refer to values stored in holder fields before the
// holder reference is stored in the location for which we want to avoid the read barrier.
// Since the stored holder reference points to an object with the initialized constant or
// invariant, when we start a new GC and that holder instance becomes a from-space object
// both the from-space and to-space versions shall hold the same constant or invariant.
//
// While correct inter-thread memory visibility needs to be ensured for these constants and
// invariants, it needs to be equally ensured for non-moving GC types, so read barriers or
// their avoidance do not place any additional constraints on inter-thread synchronization.
//
// References read without a read barrier must not remain live at the next suspend point,
// with the exception of references to un-reclaimable immune space objects.
//
// For un-reclaimable immune space objects, we rely on graying dirty objects in the FlipCallback
// pause (we try to gray them just before flipping thread roots but the FlipCallback has to re-scan
// for newly dirtied objects) and clean objects conceptually become black at that point
// (marking them through is a no-op as all reference fields must also point to immune spaces),
// so mutator threads can never miss a read barrier as they never see white immune space object.
//
// Examples:
//
// The j.l.Class contains many constant fields and invariants:
//   - primitive type is constant (primitive classes are pre-initialized in the boot image,
//     or created in early single-threaded stage when running without boot image; non-primitive
//     classes keep the value 0 from the Class object allocation),
//   - element type is constant (initialized during array class object allocation, null otherwise),
//   - access flags are mutable but the proxy class bit is an invariant set during class creation,
//   - once the class is resolved, the class status is still mutable but it shall remain resolved,
//     being a resolved is an invariant from that point on,
//   - once a class becomes erroneous, the class status shall be constant (and unresolved
//     erroneous class shall not become resolved).
// This allows reading a chain of element type references for any number of array dimensions
// without read barrier to find the (non-array) element class and check whether it's primitive,
// or proxy class. When creating an array class, the element type is already either resolved or
// unresolved erroneous and neither shall change, so we can also check these invariants (but not
// resolved erroneous because that is not an invariant from the creation of the array class).
//
// The superclass becomes constant during the ClassStatus::kIdx stage, so it's safe to treat it
// as constant when reading from locations that can reference only resolved classes.
enum EXPORT ReadBarrierOption {
  kWithReadBarrier,       // Perform a read barrier.
  kWithoutReadBarrier,    // Don't perform a read barrier.
  kWithFromSpaceBarrier,  // Get the from-space address for the given to-space address. Used by CMC
};

}  // namespace art

#endif  // ART_RUNTIME_READ_BARRIER_OPTION_H_
