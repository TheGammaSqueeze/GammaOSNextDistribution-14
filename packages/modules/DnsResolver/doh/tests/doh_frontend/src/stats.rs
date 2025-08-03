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

//! Statistics for tests to check server status.

#[derive(Debug, Default, Clone)]
#[repr(C)]
pub struct Stats {
    /// The number of accumulated DoH queries that are received.
    pub queries_received: u32,
    /// The number of accumulated QUIC connections accepted.
    pub connections_accepted: u32,
    /// The number of QUIC connections alive.
    pub alive_connections: u32,
    /// The number of QUIC connections using session resumption.
    pub resumed_connections: u32,
    /// The number of QUIC connections that received early data.
    pub early_data_connections: u32,
}

impl Stats {
    pub fn new() -> Self {
        Default::default()
    }
}
