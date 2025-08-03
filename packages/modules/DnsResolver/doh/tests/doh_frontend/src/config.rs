/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Runtime configuration for DohFrontend.

use std::default::Default;

/// Default value for max_idle_timeout transport parameter.
pub const QUICHE_IDLE_TIMEOUT_MS: u64 = 10_000;

/// Default value for these transport parameters:
/// - initial_max_data
/// - initial_max_stream_data_bidi_local
/// - initial_max_stream_data_bidi_remote
/// - initial_max_stream_data_uni
const MAX_BUFFER_SIZE: u64 = 1_000_000;

/// Default value for initial_max_streams_bidi transport parameter.
pub const MAX_STREAMS_BIDI: u64 = 100;

#[derive(Debug, Default)]
pub struct Config {
    pub delay_queries: i32,
    pub block_sending: bool,
    pub max_idle_timeout: u64,
    pub max_buffer_size: u64,
    pub max_streams_bidi: u64,
    pub reset_stream_id: Option<u64>,
}

impl Config {
    pub fn new() -> Self {
        Self {
            max_idle_timeout: QUICHE_IDLE_TIMEOUT_MS,
            max_buffer_size: MAX_BUFFER_SIZE,
            max_streams_bidi: MAX_STREAMS_BIDI,
            ..Default::default()
        }
    }
}
