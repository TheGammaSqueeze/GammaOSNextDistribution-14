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

//! This module provides the types of the parameters or returned data of the public interfaces.

pub(super) mod utils;

pub mod aliro_app_config_params;
pub mod app_config_params;
pub mod ccc_app_config_params;
pub mod ccc_started_app_config_params;
pub mod fira_app_config_params;
pub mod uci_packets;

// Re-export params from all of the sub-modules.
pub use aliro_app_config_params::*;
pub use app_config_params::*;
pub use ccc_app_config_params::*;
pub use ccc_started_app_config_params::*;
pub use fira_app_config_params::*;
pub use uci_packets::*;
