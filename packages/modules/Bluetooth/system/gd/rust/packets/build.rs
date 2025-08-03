//
//  Copyright 2021 Google, Inc.
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

use pdl_compiler;
use std::env;
use std::fs::File;
use std::io::Write;
use std::path::{Path, PathBuf};

fn main() {
    let pdl_root = match env::var("PLATFORM_SUBDIR") {
        Ok(dir) => PathBuf::from(dir).join("bt/pdl"),
        // Currently at //platform2/gd/rust/rust/packets
        Err(_) => {
            PathBuf::from(env::current_dir().unwrap()).join("../../../pdl").canonicalize().unwrap()
        }
    };

    let in_file = pdl_root.join("hci/hci_packets.pdl");
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let out_file = out_dir.join("hci_packets.rs");

    let packets_prebuilt = match env::var("HCI_PACKETS_PREBUILT") {
        Ok(dir) => PathBuf::from(dir),
        Err(_) => PathBuf::from("hci_packets.rs"),
    };

    if Path::new(packets_prebuilt.as_os_str()).exists() {
        std::fs::copy(
            packets_prebuilt.as_os_str().to_str().unwrap(),
            out_file.as_os_str().to_str().unwrap(),
        )
        .unwrap();
    } else {
        let mut sources = pdl_compiler::ast::SourceDatabase::new();
        let file = pdl_compiler::parser::parse_file(
            &mut sources,
            &in_file.into_os_string().into_string().unwrap(),
        )
        .expect("failed to parse input pdl file");
        let file =
            pdl_compiler::analyzer::analyze(&file).expect("failed to validate input pdl file");
        let generated = pdl_compiler::backends::rust::generate(&sources, &file);

        let mut f = File::create(out_file).unwrap();
        f.write_all(generated.as_bytes()).unwrap();

        println!("cargo:rerun-if-changed=build.rs");
    }
}
