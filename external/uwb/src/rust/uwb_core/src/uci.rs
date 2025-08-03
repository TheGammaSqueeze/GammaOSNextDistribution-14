// Copyright 2022, The Android Open Source Project
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

//! This module provides the functionalities related to UWB Command Interface (UCI).

mod command;
mod message;
mod pcapng_block;
mod response;
mod timeout_uci_hal;

pub(crate) mod error;
pub(crate) mod notification;
pub(crate) mod uci_manager;

pub mod pcapng_uci_logger_factory;
pub mod uci_hal;
pub mod uci_logger;
pub mod uci_logger_factory;
pub mod uci_logger_pcapng;
pub mod uci_manager_sync;

#[cfg(test)]
pub(crate) mod mock_uci_hal;
#[cfg(test)]
pub(crate) mod mock_uci_logger;
#[cfg(any(test, feature = "mock-utils"))]
pub mod mock_uci_manager;

// Re-export the public elements.
pub use command::UciCommand;
pub use notification::{
    CoreNotification, DataRcvNotification, RadarDataRcvNotification, RadarSweepData,
    RangingMeasurements, SessionNotification, SessionRangeData, UciNotification,
};
pub use uci_hal::{NopUciHal, UciHal, UciHalPacket};
pub use uci_logger_factory::{NopUciLoggerFactory, UciLoggerFactory};
pub use uci_manager::UciManagerImpl;
