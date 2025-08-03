// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use lazy_static::lazy_static;
use std::sync::{Arc, RwLock};

use crate::captures::capture::Captures;
use crate::version::get_version;

lazy_static! {
    static ref RESOURCES: Resource = Resource::new();
}

/// Resource struct includes all the global and possibly shared
/// resources for netsim.  Each field within Resource should be an Arc
/// protected by a RwLock or Mutex.
pub struct Resource {
    version: String,
    captures: Arc<RwLock<Captures>>,
}

impl Resource {
    pub fn new() -> Self {
        Self { version: get_version(), captures: Arc::new(RwLock::new(Captures::new())) }
    }

    #[allow(dead_code)]
    pub fn get_version_resource(self) -> String {
        self.version
    }
}

pub fn clone_captures() -> Arc<RwLock<Captures>> {
    Arc::clone(&RESOURCES.captures)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        let resource = Resource::new();
        assert_eq!(get_version(), resource.get_version_resource());
    }
}
