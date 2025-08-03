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

use hashbrown::HashSet;
use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;

const MAX_RESERVE: usize = 1024;

#[derive(Arbitrary, Debug, Eq, Hash, PartialEq)]
enum Data {
    A,
    B,
    Int { val: u8 },
}

#[derive(Arbitrary, Debug)]
enum HashSetMethods {
    Insert { value: Data },
    Remove { value: Data },
    Contains { value: Data },
    Get { value: Data },
    GetOrInsert { value: Data },
    Iter,
    Drain,
    Clear,
    Reserve { additional: usize },
    ShrinkToFit,
    ShrinkTo { min_capacity: usize },
}

fuzz_target!(|commands: Vec<HashSetMethods>| {
    let mut set = HashSet::new();
    for command in commands {
        match command {
            HashSetMethods::Insert { value } => {
                set.insert(value);
            }
            HashSetMethods::Remove { value } => {
                set.remove(&value);
            }
            HashSetMethods::Contains { value } => {
                set.contains(&value);
            }
            HashSetMethods::Get { value } => {
                set.get(&value);
            }
            HashSetMethods::GetOrInsert { value } => {
                set.get_or_insert(value);
            }
            HashSetMethods::Iter => {
                std::hint::black_box(set.iter().count());
            }
            HashSetMethods::Drain => {
                std::hint::black_box(set.drain().count());
            }
            HashSetMethods::Clear => {
                set.clear();
            }
            HashSetMethods::Reserve { additional } => {
                // Avoid allocating too much memory and crashing the fuzzer.
                set.reserve(additional % MAX_RESERVE);
            }
            HashSetMethods::ShrinkToFit => {
                set.shrink_to_fit();
            }
            HashSetMethods::ShrinkTo { min_capacity } => {
                set.shrink_to(min_capacity % MAX_RESERVE);
            }
        }
    }
});
