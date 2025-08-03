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

use hashbrown::HashMap;
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
enum HashMapMethods {
    Insert { key: Data, value: Data },
    Remove { key: Data },
    ContainsKey { key: Data },
    Get { key: Data },
    EntryOrInsert { key: Data, value: Data },
    Iter,
    Drain,
    Clear,
    Reserve { additional: usize },
    ShrinkToFit,
    ShrinkTo { min_capacity: usize },
}

fuzz_target!(|commands: Vec<HashMapMethods>| {
    let mut map = HashMap::new();
    for command in commands {
        match command {
            HashMapMethods::Insert { key, value } => {
                map.insert(key, value);
            }
            HashMapMethods::Remove { key } => {
                map.remove(&key);
            }
            HashMapMethods::ContainsKey { key } => {
                map.contains_key(&key);
            }
            HashMapMethods::Get { key } => {
                map.get(&key);
            }
            HashMapMethods::EntryOrInsert { key, value } => {
                map.entry(key).or_insert(value);
            }
            HashMapMethods::Iter => {
                std::hint::black_box(map.iter().count());
            }
            HashMapMethods::Drain => {
                std::hint::black_box(map.drain().count());
            }
            HashMapMethods::Clear => {
                map.clear();
            }
            HashMapMethods::Reserve { additional } => {
                // Avoid allocating too much memory and crashing the fuzzer.
                map.reserve(additional % MAX_RESERVE);
            }
            HashMapMethods::ShrinkToFit => {
                map.shrink_to_fit();
            }
            HashMapMethods::ShrinkTo { min_capacity } => {
                map.shrink_to(min_capacity % MAX_RESERVE);
            }
        }
    }
});
