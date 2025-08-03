// Copyright (C) 2021 The Android Open Source Project
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

//! Provides utilities for Android user ids.

pub use cutils_bindgen::AID_KEYSTORE;
pub use cutils_bindgen::AID_ROOT;
pub use cutils_bindgen::AID_SHELL;
pub use cutils_bindgen::AID_SYSTEM;
pub use cutils_bindgen::AID_USER_OFFSET;

/// Gets the user id from a uid.
pub fn multiuser_get_user_id(uid: u32) -> u32 {
    uid / AID_USER_OFFSET
}

/// Gets the app id from a uid.
pub fn multiuser_get_app_id(uid: u32) -> u32 {
    uid % AID_USER_OFFSET
}

/// Gets the uid from a user id and app id.
pub fn multiuser_get_uid(user_id: u32, app_id: u32) -> u32 {
    (user_id * AID_USER_OFFSET) + (app_id % AID_USER_OFFSET)
}
