// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, item 2.0 (the "License");
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

//! This file defines UciLoggerFactory, which manages the shared log file for multiple UciManager
//! instances.

use crate::uci::uci_logger::{NopUciLogger, UciLogger};

/// Trait definition for UciLoggerFactory, which builds UciLoggers that shares a single log file
/// created by this struct.
/// structs implementing trait shall be ready to accept log at initialization, and the Loggers built
/// shall remain valid after the factory goes out of scope.
pub trait UciLoggerFactory {
    /// Type of UciLogger used.
    type Logger: UciLogger;
    /// Builds a UciLogger whose log would route to this UciLoggerFactory.
    ///
    /// If a logger with same name is built, the returned UciLogger should work as a clone of the
    /// previous one.
    fn build_logger(&mut self, chip_id: &str) -> Option<Self::Logger>;
}

/// The UciLoggerFactory implementation that always builds NopUciLogger.
#[derive(Default)]
pub struct NopUciLoggerFactory {}
impl UciLoggerFactory for NopUciLoggerFactory {
    type Logger = NopUciLogger;

    fn build_logger(&mut self, _chip_id: &str) -> Option<NopUciLogger> {
        Some(NopUciLogger::default())
    }
}
