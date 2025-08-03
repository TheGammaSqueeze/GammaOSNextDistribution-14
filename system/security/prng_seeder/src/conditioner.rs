// Copyright (C) 2022 The Android Open Source Project
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

use std::{fs::File, io::Read};

use anyhow::{ensure, Context, Result};
use log::debug;
use tokio::io::AsyncReadExt;

use crate::drbg;

const SEED_FOR_CLIENT_LEN: usize = 496;
const NUM_REQUESTS_PER_RESEED: u32 = 256;

pub struct ConditionerBuilder {
    hwrng: File,
    rg: drbg::Drbg,
}

impl ConditionerBuilder {
    pub fn new(mut hwrng: File) -> Result<ConditionerBuilder> {
        let mut et: drbg::Entropy = [0; drbg::ENTROPY_LEN];
        hwrng.read_exact(&mut et).context("hwrng.read_exact in new")?;
        let rg = drbg::Drbg::new(&et)?;
        Ok(ConditionerBuilder { hwrng, rg })
    }

    pub fn build(self) -> Conditioner {
        Conditioner {
            hwrng: tokio::fs::File::from_std(self.hwrng),
            rg: self.rg,
            requests_since_reseed: 0,
        }
    }
}

pub struct Conditioner {
    hwrng: tokio::fs::File,
    rg: drbg::Drbg,
    requests_since_reseed: u32,
}

impl Conditioner {
    pub async fn reseed_if_necessary(&mut self) -> Result<()> {
        if self.requests_since_reseed >= NUM_REQUESTS_PER_RESEED {
            debug!("Reseeding DRBG");
            let mut et: drbg::Entropy = [0; drbg::ENTROPY_LEN];
            self.hwrng.read_exact(&mut et).await.context("hwrng.read_exact in reseed")?;
            self.rg.reseed(&et)?;
            self.requests_since_reseed = 0;
        }
        Ok(())
    }

    pub fn request(&mut self) -> Result<[u8; SEED_FOR_CLIENT_LEN]> {
        ensure!(self.requests_since_reseed < NUM_REQUESTS_PER_RESEED, "Not enough reseeds");
        let mut seed_for_client = [0u8; SEED_FOR_CLIENT_LEN];
        self.rg.generate(&mut seed_for_client)?;
        self.requests_since_reseed += 1;
        Ok(seed_for_client)
    }
}
