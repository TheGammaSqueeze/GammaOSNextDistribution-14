// Copyright 2020, The Android Open Source Project
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

//! This is a helper crate for using bindgen as a library from within
//! Android's build system. Some functionality (such as the type selection
//! heuristic) is not available on the command line, and so the library
//! interface may be necessary. Bindgen also needs to receive configuration
//! from Soong however to find appropriate headers, set global cflags, etc.
//!
//! This crate provides the ability to run a hooked version of the command
//! line bindgen tool, with the ability to call a user-provided transformation
//! on the the builder before it is used.

use bindgen;
use bindgen_cli;
use std::env;

/// Takes in a function describing adjustments to make to a builder
/// initialized by the command line. `build(|x| x)` is equivalent to
/// running bindgen. When converting a build.rs, you will want to convert the
/// additional configuration they do into a function, then pass it to `build`
/// inside your main function.
pub fn build<C: FnOnce(bindgen::Builder) -> bindgen::Builder>(configure: C) {
    env_logger::init();

    match bindgen_cli::builder_from_flags(env::args()) {
        Ok((builder, output, _)) => {
            configure(builder)
                .generate()
                .expect("Unable to generate bindings")
                .write(output)
                .expect("Unable to write output");
        }
        Err(error) => {
            eprintln!("{}", error);
            std::process::exit(1);
        }
    };
}
