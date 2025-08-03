//
//  Copyright 2023 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

use std::env;
use std::fs::File;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn main() {
    generate_packets();
}

fn generate_packets() {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let out_file = File::create(out_dir.join("l2cap_packets.rs")).unwrap();
    let in_file = PathBuf::from("l2cap_packets.pdl");

    // Expect pdlc to be in the PATH
    println!("cargo:rerun-if-changed={}", in_file.display());
    let output = Command::new("pdlc")
        .arg("--output-format")
        .arg("rust")
        .arg(in_file)
        .stdout(Stdio::from(out_file))
        .output()
        .unwrap();

    println!(
        "Status: {}, stderr: {}",
        output.status,
        String::from_utf8_lossy(output.stderr.as_slice())
    );
}
