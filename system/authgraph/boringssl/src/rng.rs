// Copyright 2023 Google LLC
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
////////////////////////////////////////////////////////////////////////////////

//! BoringSSL-based implementation of random number generation.
use authgraph_core::traits::Rng;

/// [`Rng`] implementation based on BoringSSL.
#[derive(Clone, Default)]
pub struct BoringRng;

impl Rng for BoringRng {
    fn fill_bytes(&self, nonce: &mut [u8]) {
        openssl::rand::rand_bytes(nonce).unwrap(); // safe: BoringSSL's RAND_bytes() never fails
    }
    fn box_clone(&self) -> Box<dyn Rng> {
        Box::new(self.clone())
    }
}
