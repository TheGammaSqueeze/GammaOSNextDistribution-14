/*
 * Copyright (C) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Reports redundant AIDL libraries included in a partition.

use anyhow::{Context, Result};
use clap::Parser;
use std::collections::BTreeMap;
use std::fs::File;
use std::io::BufReader;
use std::path::{Path, PathBuf};

#[derive(Parser, Debug)]
#[structopt()]
struct Opt {
    /// JSON file with list of files installed in a partition, e.g. "$OUT/installed-files.json".
    #[clap(long)]
    installed_files_json: PathBuf,

    /// JSON file with metadata for AIDL interfaces. Optional, but fewer checks are performed when
    /// unset.
    #[clap(long)]
    aidl_metadata_json: Option<PathBuf>,
}

/// "aidl_metadata.json" entry.
#[derive(Debug, serde::Deserialize)]
struct AidlInterfaceMetadata {
    /// Name of module defining package.
    name: String,
}

/// "installed-files.json" entry.
#[derive(Debug, serde::Deserialize)]
struct InstalledFile {
    /// Full file path.
    #[serde(rename = "Name")]
    name: String,
    /// File size.
    #[serde(rename = "Size")]
    size: u64,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
enum LibDir {
    Lib,
    Lib64,
}

/// An instance of an AIDL interface lib.
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord)]
struct AidlInstance {
    installed_path: String,
    size: u64,
    name: String,
    variant: String, // e.g. "ndk" or "cpp"
    version: usize,
    lib_dir: LibDir,
}

/// Deserializes a JSON file at `path` into an object of type `T`.
fn read_json_file<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T> {
    let file = File::open(path).with_context(|| format!("failed to open: {}", path.display()))?;
    serde_json::from_reader(BufReader::new(file))
        .with_context(|| format!("failed to read: {}", path.display()))
}

/// Extracts AIDL lib info an `InstalledFile`, mainly by parsing the file path. Returns `None` if
/// it doesn't look like an AIDL lib.
fn extract_aidl_instance(installed_file: &InstalledFile) -> Option<AidlInstance> {
    // example: android.hardware.security.keymint-V2-ndk.so
    let lib_regex = regex::Regex::new(r".*/(lib|lib64)/([^-]*)-V(\d+)-([^.]+)\.")
        .expect("failed to parse regex");
    let captures = lib_regex.captures(&installed_file.name)?;
    let (dir, name, version, variant) = (&captures[1], &captures[2], &captures[3], &captures[4]);
    Some(AidlInstance {
        installed_path: installed_file.name.clone(),
        size: installed_file.size,
        name: name.to_string(),
        variant: variant.to_string(),
        version: version.parse().unwrap(),
        lib_dir: if dir == "lib64" { LibDir::Lib64 } else { LibDir::Lib },
    })
}

fn main() -> Result<()> {
    let args = Opt::parse();

    // Read the metadata file if available.
    let metadata_list: Option<Vec<AidlInterfaceMetadata>> = match &args.aidl_metadata_json {
        Some(aidl_metadata_json) => read_json_file(aidl_metadata_json)?,
        None => None,
    };
    let is_valid_aidl_lib = |name: &str| match &metadata_list {
        Some(x) => x.iter().any(|metadata| metadata.name == name),
        None => true,
    };

    // Read the "installed-files.json" and create a list of AidlInstance.
    let installed_files: Vec<InstalledFile> = read_json_file(&args.installed_files_json)?;
    let instances: Vec<AidlInstance> = installed_files
        .iter()
        .filter_map(extract_aidl_instance)
        .filter(|instance| {
            if !is_valid_aidl_lib(&instance.name) {
                eprintln!(
                    "WARNING: {} looks like an AIDL lib, but has no metadata",
                    &instance.installed_path
                );
                return false;
            }
            true
        })
        .collect();

    // Group redundant AIDL lib instances together.
    let groups: BTreeMap<(String, LibDir), Vec<&AidlInstance>> =
        instances.iter().fold(BTreeMap::new(), |mut acc, x| {
            let key = (x.name.clone(), x.lib_dir);
            acc.entry(key).or_default().push(x);
            acc
        });
    let mut total_wasted_bytes = 0;
    for (group_key, mut instances) in groups {
        if instances.len() > 1 {
            instances.sort();
            // Prefer the highest version, break ties favoring ndk.
            let preferred_instance = instances
                .iter()
                .max_by_key(|x| (x.version, i32::from(x.variant == "ndk")))
                .unwrap();
            let wasted_bytes: u64 =
                instances.iter().filter(|x| *x != preferred_instance).map(|x| x.size).sum();
            println!("Found redundant AIDL instances for {:?}", group_key);
            for instance in instances.iter() {
                println!(
                    "\t{}\t({:.2} KiB){}",
                    instance.installed_path,
                    instance.size as f64 / 1024.0,
                    if instance == preferred_instance { " <- preferred" } else { "" }
                );
            }
            total_wasted_bytes += wasted_bytes;
            println!("\t(potential savings: {:.2} KiB)", wasted_bytes as f64 / 1024.0);
            println!();
        }
    }
    println!("total potential savings: {:.2} KiB", total_wasted_bytes as f64 / 1024.0);

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::CommandFactory;

    #[test]
    fn verify_opt() {
        Opt::command().debug_assert();
    }
}
