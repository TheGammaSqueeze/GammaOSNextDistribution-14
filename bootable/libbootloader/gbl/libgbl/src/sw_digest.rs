// Copyright 2024, The Android Open Source Project
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

//! Software implementation for GBL Digest trait.
//!
//! `sw_digest` feature is needed to use this implementation.

extern crate ring;

use crate::digest::{Algorithm, Context, Digest};

impl From<Algorithm> for &ring::digest::Algorithm {
    fn from(val: Algorithm) -> Self {
        match val {
            Algorithm::SHA256 => &ring::digest::SHA256,
            Algorithm::SHA512 => &ring::digest::SHA512,
        }
    }
}

/// Software implementation for digest Context
pub struct SwContext {
    algorithm: Algorithm,
    ring_context: ring::digest::Context,
}

impl Context for SwContext {
    type Digest = SwDigest;

    fn new(algorithm: Algorithm) -> Self
    where
        Self: Sized,
    {
        Self { algorithm, ring_context: ring::digest::Context::new(algorithm.into()) }
    }

    fn update(&mut self, input: &[u8]) {
        self.ring_context.update(input)
    }

    fn finish(self) -> SwDigest {
        SwDigest { algorithm: self.algorithm, ring_digest: self.ring_context.finish() }
    }

    fn algorithm(&self) -> &Algorithm {
        &self.algorithm
    }
}

/// Software implementation of Digest.
pub struct SwDigest {
    algorithm: Algorithm,
    ring_digest: ring::digest::Digest,
}

impl AsRef<[u8]> for SwDigest {
    fn as_ref(&self) -> &[u8] {
        self.ring_digest.as_ref()
    }
}

impl Digest for SwDigest {
    fn algorithm(&self) -> &Algorithm {
        &self.algorithm
    }
}

mod tests {
    use super::*;

    // Compute digest on provided input using algorithm
    fn digest(algorithm: Algorithm, input: &[u8]) -> SwDigest {
        let mut ctx = SwContext::new(algorithm);
        ctx.update(input);
        ctx.finish()
    }

    #[test]
    fn test_swdigest_sha256() {
        let input = b"abc";
        let expected =
            hex::decode("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD")
                .unwrap();
        assert_eq!(digest(Algorithm::SHA256, input).as_ref(), expected);
    }

    #[test]
    fn test_swdigest_sha512() {
        assert_eq!(
            digest(Algorithm::SHA512, b"abc").as_ref(),
            hex::decode(concat!(
                "DDAF35A193617ABACC417349AE20413112E6FA4E89A97EA2",
                "0A9EEEE64B55D39A2192992A274FC1A836BA3C23A3FEEBBD",
                "454D4423643CE80E2A9AC94FA54CA49F",
            ))
            .unwrap()
        );
    }

    #[test]
    fn test_swdigest_sha_partial() {
        let input = b"abc";
        let expected =
            hex::decode("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD")
                .unwrap();

        let mut ctx = SwContext::new(Algorithm::SHA256);
        for i in input.chunks(input.len()) {
            ctx.update(i);
        }

        assert_eq!(ctx.finish().as_ref(), expected);
    }
}
