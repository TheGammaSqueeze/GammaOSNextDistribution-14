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

//! # Zip Artifact Class

use std::{
    fs::{read_dir, remove_file, File},
    io::{Read, Result, Write},
    path::PathBuf,
};

use log::warn;
use zip::{result::ZipResult, write::FileOptions, ZipWriter};

use crate::system::netsimd_temp_dir;

use super::time_display::{file_current_time, TimeZone};

/// Recurse all files in root and put it in Vec<PathBuf>
fn recurse_files(root: &PathBuf) -> Result<Vec<(PathBuf, String)>> {
    let mut result = Vec::new();
    // Read all entries in the given root directory
    let entries = read_dir(root)?;
    for entry in entries {
        let entry = entry?;
        let meta = entry.metadata()?;
        // Perform recursion if it's a directory
        if meta.is_dir() {
            let mut subdir = recurse_files(&entry.path())?;
            result.append(&mut subdir);
        }
        if meta.is_file() {
            if let Some(filename) = entry
                .path()
                .file_name()
                .and_then(|os_name| os_name.to_str())
                .map(|str_name| str_name.to_string())
            {
                result.push((entry.path(), filename));
            } else {
                warn!("Unable to fetch filename for file: {:?}", entry.path());
            }
        }
    }
    Ok(result)
}

/// Fetch all zip files in root and put it in sorted Vec<PathBuf>
fn fetch_zip_files(root: &PathBuf) -> Result<Vec<PathBuf>> {
    // Read all entries in the given root directory
    // Push path to result if name matches "netsim_artifacts_*.zip"
    let mut result: Vec<PathBuf> = read_dir(root)?
        .filter_map(|e| e.ok())
        .map(|e| e.path())
        .filter(|path| {
            path.is_file()
                && path.file_name().and_then(|os_name| os_name.to_str()).map_or(false, |filename| {
                    filename.starts_with("netsim_artifacts_") && filename.ends_with(".zip")
                })
        })
        .collect();
    // Sort the zip files by timestamp from oldest to newest
    result.sort();
    Ok(result)
}

/// Remove set number of zip files
pub fn remove_zip_files() -> Result<()> {
    // TODO(b/305012017): Add parameter for keeping some number of zip files
    // Fetch all zip files in netsimd_temp_dir
    let zip_files = fetch_zip_files(&netsimd_temp_dir())?;
    for file in zip_files {
        if let Err(err) = std::fs::remove_file(&file) {
            warn!("Removing {file:?} error: {err:?}");
        }
    }
    Ok(())
}

/// Zip the whole netsimd temp directory and store it in temp directory.
pub fn zip_artifacts() -> ZipResult<()> {
    // Fetch all files in netsimd_temp_dir
    let root = netsimd_temp_dir();
    let files = recurse_files(&root)?;

    // Define PathBuf for zip file
    let zip_file =
        root.join(format!("netsim_artifacts_{}.zip", file_current_time(TimeZone::Local)));

    // Create a new ZipWriter
    let mut zip_writer = ZipWriter::new(File::create(zip_file)?);
    let mut buffer = Vec::new();

    // Put each artifact files into zip file
    for (file, filename) in files {
        // Avoid zip files
        if filename.starts_with("netsim_artifacts") {
            continue;
        }

        // Write to zip file
        zip_writer.start_file(&filename, FileOptions::default())?;
        let mut f = File::open(&file)?;
        f.read_to_end(&mut buffer)?;
        zip_writer.write_all(&buffer)?;
        buffer.clear();

        // Remove the file once written except for netsim log and json files
        if filename.starts_with("netsim_")
            && (filename.ends_with(".log") || filename.ends_with(".json"))
        {
            continue;
        }
        remove_file(file)?;
    }

    // Finish writing zip file
    zip_writer.finish()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_temp_dir() -> PathBuf {
        std::env::temp_dir().join("netsim-test").join(format!("{:?}", std::thread::current()))
    }

    #[test]
    fn test_recurse_files() {
        let path = test_temp_dir();

        // Create the temporary directory
        if std::fs::create_dir_all(&path).is_err() {
            return; // return if test environment disallows file creation
        }

        // Create a file
        let file = path.join("hello.txt");
        if File::create(&file).is_err() {
            return; // return if test environment disallows file creation
        }

        // Create a folder and a file inside
        let folder = path.join("folder");
        std::fs::create_dir_all(&folder).unwrap();
        let nested_file = folder.join("world.txt");
        File::create(&nested_file).unwrap();

        // Recurse Files and check the contents
        let files_result = recurse_files(&path);
        assert!(files_result.is_ok());
        let files = files_result.unwrap();
        assert_eq!(files.len(), 2);
        assert!(files.contains(&(file, "hello.txt".to_string())));
        assert!(files.contains(&(nested_file, "world.txt".to_string())));
    }

    #[test]
    fn test_fetch_zip_files() {
        let path = test_temp_dir();

        // Create the temporary directory
        if std::fs::create_dir_all(&path).is_err() {
            return; // return if test environment disallows file creation
        }

        // Create multiple zip files in random order of timestamps
        let zip_file_1 = path.join("netsim_artifacts_2024-01-01.zip");
        let zip_file_2 = path.join("netsim_artifacts_2022-12-31.zip");
        let zip_file_3 = path.join("netsim_artifacts_2023-06-01.zip");
        let zip_file_faulty = path.join("netsim_arts_2000-01-01.zip");
        if File::create(&zip_file_1).is_err() {
            return; // return if test environment disallows file creation
        }
        File::create(&zip_file_2).unwrap();
        File::create(&zip_file_3).unwrap();
        File::create(zip_file_faulty).unwrap();

        // Fetch all zip files and check the contents if it is in order
        let files_result = fetch_zip_files(&path);
        assert!(files_result.is_ok());
        let files = files_result.unwrap();
        assert_eq!(files.len(), 3);
        assert_eq!(files.first().unwrap(), &zip_file_2);
        assert_eq!(files.get(1).unwrap(), &zip_file_3);
        assert_eq!(files.get(2).unwrap(), &zip_file_1);
    }
}
