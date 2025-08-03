/*
 * Copyright (C) 2023 The Android Open Source Project
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

//! Provides AApexInfo (name, version) from the calling process

use apex_manifest::apex_manifest::ApexManifest;
use protobuf::Message;
use std::env;
use std::ffi::CString;
use std::fs::File;
use std::path::{Path, PathBuf};

#[derive(Debug)]
pub enum AApexInfoError {
    /// The executable's path is not from an APEX
    PathNotFromApex(PathBuf),
    /// Fail to get the current executable's path
    ExePathUnavailable(std::io::Error),
    /// Fail to read apex_manifest.pb from an APEX
    InvalidApex(String),
}

/// AApexInfo is used as an opaque object from FFI clients. This just wraps
/// ApexManifest protobuf message.
///
/// NOTE: that we don't want to provide too many details about APEX. It provides
/// minimal information (for now, name and version) only for the APEX where the
/// current process is loaded from.
pub struct AApexInfo {
    pub name: CString,
    pub version: i64,
}

impl AApexInfo {
    /// Returns AApexInfo object when called by an executable from an APEX.
    pub fn create() -> Result<Self, AApexInfoError> {
        let exe_path = env::current_exe().map_err(AApexInfoError::ExePathUnavailable)?;
        let manifest_path = get_apex_manifest_path(exe_path)?;
        let manifest = parse_apex_manifest(manifest_path)?;
        Ok(AApexInfo {
            name: CString::new(manifest.name)
                .map_err(|err| AApexInfoError::InvalidApex(format!("{err:?}")))?,
            version: manifest.version,
        })
    }
}

/// Returns the apex_manifest.pb path when a given path belongs to an apex.
fn get_apex_manifest_path<P: AsRef<Path>>(path: P) -> Result<PathBuf, AApexInfoError> {
    let remain = path
        .as_ref()
        .strip_prefix("/apex")
        .map_err(|_| AApexInfoError::PathNotFromApex(path.as_ref().to_owned()))?;
    let apex_name = remain
        .iter()
        .next()
        .ok_or_else(|| AApexInfoError::PathNotFromApex(path.as_ref().to_owned()))?;
    Ok(Path::new("/apex").join(apex_name).join("apex_manifest.pb"))
}

/// Parses the apex_manifest.pb protobuf message from a given path.
fn parse_apex_manifest<P: AsRef<Path>>(path: P) -> Result<ApexManifest, AApexInfoError> {
    let mut f = File::open(path).map_err(|err| AApexInfoError::InvalidApex(format!("{err:?}")))?;
    Message::parse_from_reader(&mut f).map_err(|err| AApexInfoError::InvalidApex(format!("{err:?}")))
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn test_get_apex_manifest_path() {
        assert_eq!(
            get_apex_manifest_path("/apex/com.android.foo/bin/foo").unwrap(),
            PathBuf::from("/apex/com.android.foo/apex_manifest.pb")
        );
        assert!(get_apex_manifest_path("/apex/").is_err());
        assert!(get_apex_manifest_path("/apex").is_err());
        assert!(get_apex_manifest_path("/com.android.foo/bin/foo").is_err());
        assert!(get_apex_manifest_path("/system/apex/com.android.foo/bin/foo").is_err());
    }
}
