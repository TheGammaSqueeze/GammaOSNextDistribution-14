// Copyright 2022 Google LLC
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

use crate::ffi::ClientResponseReadable;
use log::error;
use std::fs::File;
/// Implements handler for pcap operations
use std::io::Write;
use std::path::PathBuf;

pub struct FileHandler {
    pub file: File,
    pub path: PathBuf,
}

impl ClientResponseReadable for FileHandler {
    // function to handle writing each chunk to file
    fn handle_chunk(&self, chunk: &[u8]) {
        (&self.file)
            .write_all(chunk)
            .unwrap_or_else(|_| panic!("Unable to write to file: {}", self.path.display()));
    }
    // function to handle error response
    fn handle_error(&self, error_code: u32, error_message: &str) {
        error!(
            "Handling error code: {}, msg: {}, on file: {}",
            error_code,
            error_message,
            self.path.display()
        );
    }
}
