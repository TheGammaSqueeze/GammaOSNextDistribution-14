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

//! Inspection and manipulation of the system environment.

use std::env;
use std::path::PathBuf;

/// Get or create the netsimd temporary directory.
///
/// This is based on emu System.cpp android::base::getTempDir()
///
/// Under Forge temp directory is `$ANDROID_TMP/android-$USER/netsimd`,
/// otherwise it is `$TMP/android-$USER/netsimd`
///
pub fn netsimd_temp_dir() -> PathBuf {
    let path = netsimd_temp_dir_pathbuf();
    if !path.is_dir() {
        std::fs::create_dir_all(&path).unwrap();
    }
    path
}

/// Helper function for netsimd_temp_dir() to allow Read Only
/// Unit tests.
fn netsimd_temp_dir_pathbuf() -> PathBuf {
    // allow Forge to override the system temp
    let mut path = match env::var("ANDROID_TMP") {
        Ok(var) => PathBuf::from(var),
        _ => env::temp_dir(),
    };
    // On Windows the GetTempPath() is user-dependent so we don't need
    // to append $USER to the result -- otherwise allow multiple users
    // to co-exist on a system.
    #[cfg(not(target_os = "windows"))]
    {
        let user = match env::var("USER") {
            Ok(var) => format!("android-{}", var),
            _ => "android".to_string(),
        };
        path.push(user);
    };
    // netsimd files are stored in their own directory
    path.push("netsimd");
    path
}

/// For C++.
pub fn netsimd_temp_dir_string() -> String {
    netsimd_temp_dir().into_os_string().into_string().unwrap()
}

#[cfg(not(target_os = "windows"))]
#[cfg(test)]
mod tests {
    use super::netsimd_temp_dir_pathbuf;
    use std::env;
    use std::sync::Mutex;

    static ENV_MUTEX: Mutex<i32> = Mutex::new(0);

    #[test]
    fn test_forge() {
        let _locked = ENV_MUTEX.lock();
        env::set_var("ANDROID_TMP", "/tmp/forge");
        env::set_var("USER", "ryle");
        let tmp_dir = netsimd_temp_dir_pathbuf();
        assert_eq!(tmp_dir.to_str().unwrap(), "/tmp/forge/android-ryle/netsimd");
    }

    #[test]
    fn test_non_forge() {
        let _locked = ENV_MUTEX.lock();
        let temp_dir = env::temp_dir();
        env::remove_var("ANDROID_TMP");
        env::set_var("USER", "ryle");
        let netsimd_temp_dir = netsimd_temp_dir_pathbuf();
        assert_eq!(
            netsimd_temp_dir.to_str().unwrap(),
            temp_dir.join("android-ryle/netsimd").to_str().unwrap()
        );
    }
}
