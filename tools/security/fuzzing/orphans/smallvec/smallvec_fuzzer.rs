// Copyright 2021, The Android Open Source Project
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

#![allow(missing_docs)]
#![no_main]

use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use smallvec::{Array, SmallVec};

// Avoid allocating too much memory and crashing the fuzzer.
const MAX_SIZE_MODIFIER: usize = 1024;

#[derive(Arbitrary, Clone, Debug, PartialEq)]
enum Data {
    A,
    B,
    Int { val: u8 },
    Str { s: String },
}

#[derive(Arbitrary, Debug)]
struct FuzzInfo {
    inline_size: Size,
    commands: Vec<Command>,
}

#[derive(Arbitrary, Debug)]
enum Size {
    One,
    Two,
    Three,
    Four,
    Five,
}

#[derive(Arbitrary, Debug)]
enum Command {
    Push { value: Data },
    Pop,
    Insert { index: usize, element: Data },
    Remove { index: usize },
    SwapRemove { index: usize },
    Drain,
    Clear,
    Reserve { additional: usize },
    ReserveExact { additional: usize },
    ShrinkToFit,
    Truncate { len: usize },
    Grow { new_cap: usize },
    Dedup,
    Resize { len: usize, value: Data },
}

fuzz_target!(|info: FuzzInfo| {
    match info.inline_size {
        Size::One => do_fuzz::<[Data; 1]>(info.commands),
        Size::Two => do_fuzz::<[Data; 2]>(info.commands),
        Size::Three => do_fuzz::<[Data; 3]>(info.commands),
        Size::Four => do_fuzz::<[Data; 4]>(info.commands),
        Size::Five => do_fuzz::<[Data; 5]>(info.commands),
    }
});

fn do_fuzz<T: Array<Item = Data>>(commands: Vec<Command>) {
    let mut vec = SmallVec::<T>::new();
    for command in commands {
        match command {
            Command::Push { value } => {
                vec.push(value);
            }
            Command::Pop => {
                vec.pop();
            }
            Command::Insert { index, element } => {
                if index < vec.len() {
                    vec.insert(index, element);
                }
            }
            Command::Remove { index } => {
                if index < vec.len() {
                    vec.remove(index);
                }
            }
            Command::SwapRemove { index } => {
                if index < vec.len() {
                    vec.remove(index);
                }
            }
            Command::Drain => {
                std::hint::black_box(vec.drain(..).count());
            }
            Command::Clear => {
                vec.clear();
            }
            Command::Reserve { additional } => {
                vec.reserve(additional % MAX_SIZE_MODIFIER);
            }
            Command::ReserveExact { additional } => {
                vec.reserve_exact(additional % MAX_SIZE_MODIFIER);
            }
            Command::ShrinkToFit => {
                vec.shrink_to_fit();
            }
            Command::Truncate { len } => {
                vec.truncate(len);
            }
            Command::Grow { new_cap } => {
                let new_cap = new_cap % MAX_SIZE_MODIFIER;
                if new_cap >= vec.len() {
                    vec.grow(new_cap);
                }
            }
            Command::Dedup => {
                vec.dedup();
            }
            Command::Resize { len, value } => {
                vec.resize(len % MAX_SIZE_MODIFIER, value);
            }
        }
    }
}
