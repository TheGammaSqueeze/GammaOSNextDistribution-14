// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.setfilters.cuckoofilter;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

/** A set of predefined {@link CuckooFilterConfig.HashFunction}s. */
public enum CuckooFilterHashFunctions implements CuckooFilterConfig.HashFunction {

  /**
   * MurmurHash3 that yields 128 bit hash value.
   *
   * <p>Behavior of MurmurHash3 is fixed and should not change in the future.
   */
  MURMUR3_128() {
    @Override
    public <T> HashCode hash(T element, Funnel<? super T> funnel) {
      return Hashing.murmur3_128().hashObject(element, funnel);
    }
  }
}
