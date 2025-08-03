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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CuckooFilterHashFunctionsTest {

  @Test
  public void murmur3_128() {
    assertThat(CuckooFilterHashFunctions.MURMUR3_128.hash(100L, Funnels.longFunnel()))
        .isEqualTo(Hashing.murmur3_128().hashObject(100L, Funnels.longFunnel()));
  }
}
