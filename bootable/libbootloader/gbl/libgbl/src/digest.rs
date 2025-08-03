// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! GBL Digest trait that defines interface for hash computation.
//!
//! Software implementation available in `sw_digest` module. Specifically
//! [SwContext] and [SwDigest].

/// List of supported algorithms
#[derive(Debug, Eq, PartialEq, Clone, Copy)]
pub enum Algorithm {
    /// SHA256 algorithm
    SHA256,
    /// SHA512 algorithm
    SHA512,
}

/// Digest output trait that return algorithm and ref to the value
pub trait Digest: AsRef<[u8]> {
    /// Get digest algorithm
    fn algorithm(&self) -> &Algorithm;
}

/// Context trait that implements digesting.
/// Sha256 or Sha512.
pub trait Context {
    /// Digest type
    type Digest: Digest;

    /// Create [Context] object that can calculate digest with requested algorithm.
    ///
    /// # Arguments
    ///
    /// * algorithm - requested algorithm
    fn new(algorithm: Algorithm) -> Self;

    /// Process next portion of data for the digest.
    ///
    /// # Arguments
    ///
    /// * input - block of data to be processed
    fn update(&mut self, input: &[u8]);

    /// Finalise digest computation.
    ///
    /// Object is consumed to prevent reusing.
    fn finish(self) -> Self::Digest;

    /// The algorithm that this context is using.
    fn algorithm(&self) -> &Algorithm;
}
