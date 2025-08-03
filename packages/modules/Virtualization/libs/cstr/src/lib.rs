// Copyright 2023, The Android Open Source Project
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

//! Provide a safe const-compatible no_std macro for readable &'static CStr.

#![no_std]

/// Create &CStr out of &str literal
#[macro_export]
macro_rules! cstr {
    ($str:literal) => {{
        const S: &str = concat!($str, "\0");
        const C: &::core::ffi::CStr = match ::core::ffi::CStr::from_bytes_with_nul(S.as_bytes()) {
            Ok(v) => v,
            Err(_) => panic!("string contains interior NUL"),
        };
        C
    }};
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;

    #[test]
    fn valid_input_string() {
        let expected = CString::new("aaa").unwrap();
        assert_eq!(cstr!("aaa"), expected.as_c_str());
    }

    #[test]
    fn valid_empty_string() {
        let expected = CString::new("").unwrap();
        assert_eq!(cstr!(""), expected.as_c_str());
    }

    // As cstr!() panics at compile time, tests covering invalid inputs fail to compile!
}
