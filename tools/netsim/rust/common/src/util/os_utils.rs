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

//! # os utility functions

use std::{fs::remove_file, path::PathBuf};

use log::{error, info, warn};

use super::ini_file::IniFile;

const DEFAULT_HCI_PORT: u32 = 6402;

struct DiscoveryDir {
    root_env: &'static str,
    subdir: &'static str,
}

#[cfg(target_os = "linux")]
const DISCOVERY: DiscoveryDir = DiscoveryDir { root_env: "XDG_RUNTIME_DIR", subdir: "" };
#[cfg(target_os = "macos")]
const DISCOVERY: DiscoveryDir =
    DiscoveryDir { root_env: "HOME", subdir: "Library/Caches/TemporaryItems" };
#[cfg(target_os = "windows")]
const DISCOVERY: DiscoveryDir = DiscoveryDir { root_env: "LOCALAPPDATA", subdir: "Temp" };
#[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
compile_error!("netsim only supports linux, Mac, and Windows");

/// Get discovery directory for netsim
pub fn get_discovery_directory() -> PathBuf {
    // $TMPDIR is the temp directory on buildbots
    if let Ok(test_env_p) = std::env::var("TMPDIR") {
        return PathBuf::from(test_env_p);
    }
    let mut path = match std::env::var(DISCOVERY.root_env) {
        Ok(env_p) => PathBuf::from(env_p),
        Err(_) => {
            warn!("No discovery env for {}, using /tmp", DISCOVERY.root_env);
            PathBuf::from("/tmp")
        }
    };
    path.push(DISCOVERY.subdir);
    path
}

/// Get the filepath of netsim.ini under discovery directory
pub fn get_netsim_ini_filepath(instance_num: u16) -> PathBuf {
    let mut discovery_dir = get_discovery_directory();
    let filename = if instance_num == 1 {
        "netsim.ini".to_string()
    } else {
        format!("netsim_{instance_num}.ini")
    };
    discovery_dir.push(filename);
    discovery_dir
}

/// Remove the ini file
pub fn remove_netsim_ini(instance_num: u16) {
    match remove_file(get_netsim_ini_filepath(instance_num)) {
        Ok(_) => info!("Removed netsim ini file"),
        Err(e) => error!("Failed to remove netsim ini file: {e:?}"),
    }
}

/// Get the grpc server address for netsim
pub fn get_server_address(instance_num: u16) -> Option<String> {
    let filepath = get_netsim_ini_filepath(instance_num);
    if !filepath.exists() {
        error!("Unable to find netsim ini file: {filepath:?}");
        return None;
    }
    if !filepath.is_file() {
        error!("Not a file: {filepath:?}");
        return None;
    }
    let mut ini_file = IniFile::new(filepath);
    if let Err(err) = ini_file.read() {
        error!("Error reading ini file: {err:?}");
    }
    ini_file.get("grpc.port").map(|s| {
        if s.contains(':') {
            s.to_string()
        } else {
            format!("localhost:{}", s)
        }
    })
}

const DEFAULT_INSTANCE: u16 = 1;

/// Get the netsim instance number which is always > 0
///
/// The following priorities are used to determine the instance number:
///
/// 1. The environment variable `NETSIM_INSTANCE`.
/// 2. The CLI flag `--instance`.
/// 3. The default value `DEFAULT_INSTANCE`.
pub fn get_instance(instance_flag: Option<u16>) -> u16 {
    let instance_env: Option<u16> =
        std::env::var("NETSIM_INSTANCE").ok().and_then(|i| i.parse().ok());
    match (instance_env, instance_flag) {
        (Some(i), _) if i > 0 => i,
        (_, Some(i)) if i > 0 => i,
        (_, _) => DEFAULT_INSTANCE,
    }
}

/// Get the hci port number for netsim
pub fn get_hci_port(hci_port_flag: u32, instance: u16) -> u32 {
    // The following priorities are used to determine the HCI port number:
    //
    // 1. The CLI flag `-hci_port`.
    // 2. The environment variable `NETSIM_HCI_PORT`.
    // 3. The default value `DEFAULT_HCI_PORT`
    if hci_port_flag != 0 {
        hci_port_flag
    } else if let Ok(netsim_hci_port) = std::env::var("NETSIM_HCI_PORT") {
        netsim_hci_port.parse::<u32>().unwrap()
    } else {
        DEFAULT_HCI_PORT + (instance as u32)
    }
}

/// Get the netsim instance name used for log filename creation
pub fn get_instance_name(instance_num: Option<u16>, connector_instance: Option<u16>) -> String {
    let mut instance_name = String::new();
    let instance = get_instance(instance_num);
    if instance > 1 {
        instance_name.push_str(&format!("{instance}_"));
    }
    // Note: This does not differentiate multiple connectors to the same instance.
    if connector_instance.is_some() {
        instance_name.push_str("connector_");
    }
    instance_name
}

#[cfg(test)]
mod tests {

    use super::*;

    #[test]
    fn test_get_discovery_directory() {
        // Remove all environment variable
        std::env::remove_var(DISCOVERY.root_env);
        std::env::remove_var("TMPDIR");

        // Test with no environment variables
        let actual = get_discovery_directory();
        let mut expected = PathBuf::from("/tmp");
        expected.push(DISCOVERY.subdir);
        assert_eq!(actual, expected);

        // Test with root_env variable
        std::env::set_var(DISCOVERY.root_env, "/netsim-test");
        let actual = get_discovery_directory();
        let mut expected = PathBuf::from("/netsim-test");
        expected.push(DISCOVERY.subdir);
        assert_eq!(actual, expected);

        // Test with TMPDIR variable
        std::env::set_var("TMPDIR", "/tmpdir");
        assert_eq!(get_discovery_directory(), PathBuf::from("/tmpdir"));

        // Test netsim_ini_filepath
        assert_eq!(get_netsim_ini_filepath(1), PathBuf::from("/tmpdir/netsim.ini"));
        assert_eq!(get_netsim_ini_filepath(2), PathBuf::from("/tmpdir/netsim_2.ini"));
    }

    #[test]
    fn test_get_instance_and_instance_name() {
        // Set NETSIM_INSTANCE environment variable
        std::env::set_var("NETSIM_INSTANCE", "100");
        assert_eq!(get_instance(Some(0)), 100);
        assert_eq!(get_instance(Some(1)), 100);

        // Remove NETSIM_INSTANCE environment variable
        std::env::remove_var("NETSIM_INSTANCE");
        assert_eq!(get_instance(None), DEFAULT_INSTANCE);
        assert_eq!(get_instance(Some(0)), DEFAULT_INSTANCE);
        assert_eq!(get_instance(Some(1)), 1);

        // Default cases - instance name should be empty string
        assert_eq!(get_instance_name(None, None), "");
        assert_eq!(get_instance_name(Some(1), None), "");

        // Default instance but connector set - Expect instance name to be "connector_"
        assert_eq!(get_instance_name(None, Some(3)), "connector_");
        assert_eq!(get_instance_name(Some(1), Some(1)), "connector_");
        assert_eq!(get_instance_name(Some(1), Some(2)), "connector_");

        // Both instance and connector set - Expect instance name to be "<instance>_connector_"
        assert_eq!(get_instance_name(Some(2), Some(1)), "2_connector_");
        assert_eq!(get_instance_name(Some(3), Some(3)), "3_connector_");
    }

    #[test]
    fn test_get_hci_port() {
        // Test if hci_port flag exists
        assert_eq!(get_hci_port(1, u16::MAX), 1);
        assert_eq!(get_hci_port(1, u16::MIN), 1);

        // Remove NETSIM_HCI_PORT with hci_port_flag = 0
        std::env::remove_var("NETSIM_HCI_PORT");
        assert_eq!(get_hci_port(0, 0), DEFAULT_HCI_PORT);
        assert_eq!(get_hci_port(0, 1), DEFAULT_HCI_PORT + 1);

        // Set NETSIM_HCI_PORT
        std::env::set_var("NETSIM_HCI_PORT", "100");
        assert_eq!(get_hci_port(0, 0), 100);
        assert_eq!(get_hci_port(0, u16::MAX), 100);
    }
}
