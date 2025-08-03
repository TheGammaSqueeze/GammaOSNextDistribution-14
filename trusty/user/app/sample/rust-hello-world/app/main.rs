/*
* Copyright (C) 2022 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

// Referencing our hello_world_in_rust in the app makefile allows us to use it here.
use hello_world_in_rust;

// This calls into our code in lib.rs,
// starting the process of creating the plumbing, and starting the event loop.
fn main() {
    // This function should never return. If it does, there is an error in the main event loop.
    hello_world_in_rust::init_and_start_loop().expect("Rust Hello World service quit unexpectedly");
}
