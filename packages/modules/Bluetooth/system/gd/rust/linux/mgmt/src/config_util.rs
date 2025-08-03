use crate::state_machine::{RealHciIndex, VirtualHciIndex};

use log::LevelFilter;
use serde_json::{Map, Value};
use std::convert::TryInto;
use std::path::Path;

// Directory for Bluetooth hci devices
pub const HCI_DEVICES_DIR: &str = "/sys/class/bluetooth";

// File to store the Bluetooth daemon to use (bluez or floss)
const BLUETOOTH_DAEMON_CURRENT: &str = "/var/lib/bluetooth/bluetooth-daemon.current";

// File to store the config for BluetoothManager
const BTMANAGERD_CONF: &str = "/var/lib/bluetooth/btmanagerd.json";

/// Folder to keep files which override floss configuration
const FLOSS_SYSPROPS_OVERRIDE_DIR: &str = "/var/lib/bluetooth/sysprops.conf.d";

/// File to store bluetooth devcoredump configuration. This file is used by the
/// udev rule to copy the devcoredump state to .../device/coredump_enabled sysfs
/// entry. It is also used by the user-space crash reporter to enable/disable
/// parsing of the devcoredump.
const FLOSS_COREDUMP_CONF_PATH: &str = "/var/run/bluetooth/coredump_disabled";

/// Key used for default adapter entry.
const DEFAULT_ADAPTER_KEY: &str = "default_adapter";

/// In the absence of other values, default to hci0.
const DEFAULT_ADAPTER: VirtualHciIndex = VirtualHciIndex(0);

pub fn is_floss_enabled() -> bool {
    match std::fs::read(BLUETOOTH_DAEMON_CURRENT) {
        Ok(v) => {
            let content = std::str::from_utf8(&v);
            match content {
                Ok(version) => version.contains("floss"),
                Err(_) => false,
            }
        }
        Err(_) => false,
    }
}

pub fn write_floss_enabled(enabled: bool) -> bool {
    std::fs::write(
        BLUETOOTH_DAEMON_CURRENT,
        match enabled {
            true => "floss",
            _ => "bluez",
        },
    )
    .is_ok()
}

pub fn read_config() -> std::io::Result<String> {
    std::fs::read_to_string(BTMANAGERD_CONF)
}

pub fn get_log_level() -> Option<LevelFilter> {
    get_log_level_internal(read_config().ok()?)
}

fn get_log_level_internal(config: String) -> Option<LevelFilter> {
    serde_json::from_str::<Value>(config.as_str())
        .ok()?
        .get("log_level")?
        .as_str()?
        .parse::<LevelFilter>()
        .ok()
}

/// Returns whether hci N is enabled in config; defaults to true.
pub fn is_hci_n_enabled(hci: VirtualHciIndex) -> bool {
    read_config().ok().and_then(|config| is_hci_n_enabled_internal(config, hci)).unwrap_or(true)
}

fn is_hci_n_enabled_internal(config: String, hci: VirtualHciIndex) -> Option<bool> {
    serde_json::from_str::<Value>(config.as_str())
        .ok()?
        .get(format!("hci{}", hci.to_i32()))?
        .as_object()?
        .get("enabled")?
        .as_bool()
}

// When we initialize BluetoothManager, we need to make sure the file is a well-formatted json.
pub fn fix_config_file_format() -> bool {
    match read_config() {
        Ok(s) => match serde_json::from_str::<Value>(s.as_str()) {
            Ok(_) => true,
            _ => std::fs::write(BTMANAGERD_CONF, "{}").is_ok(),
        },
        _ => std::fs::write(BTMANAGERD_CONF, "{}").is_ok(),
    }
}

pub fn modify_hci_n_enabled(hci: VirtualHciIndex, enabled: bool) -> bool {
    if !fix_config_file_format() {
        return false;
    }
    match read_config().ok().and_then(|config| modify_hci_n_enabled_internal(config, hci, enabled))
    {
        Some(s) => std::fs::write(BTMANAGERD_CONF, s).is_ok(),
        _ => false,
    }
}

fn modify_hci_n_enabled_internal(
    config: String,
    hci: VirtualHciIndex,
    enabled: bool,
) -> Option<String> {
    let hci_str = format!("hci{}", hci.to_i32());
    let mut o = serde_json::from_str::<Value>(config.as_str()).ok()?;
    match o.get_mut(hci_str.clone()) {
        Some(section) => {
            section.as_object_mut()?.insert("enabled".to_string(), Value::Bool(enabled));
            serde_json::ser::to_string_pretty(&o).ok()
        }
        _ => {
            let mut entry_map = Map::new();
            entry_map.insert("enabled".to_string(), Value::Bool(enabled));
            o.as_object_mut()?.insert(hci_str, Value::Object(entry_map));
            serde_json::ser::to_string_pretty(&o).ok()
        }
    }
}

pub fn get_default_adapter() -> VirtualHciIndex {
    read_config()
        .ok()
        .and_then(|config| {
            serde_json::from_str::<Value>(config.as_str())
                .ok()?
                .get(DEFAULT_ADAPTER_KEY)?
                .as_i64()?
                .try_into()
                .ok()
        })
        .map(|i| VirtualHciIndex(i))
        .unwrap_or(DEFAULT_ADAPTER)
}

pub fn set_default_adapter(hci: VirtualHciIndex) -> bool {
    match read_config().ok().and_then(|config| {
        let mut cfg = serde_json::from_str::<Value>(config.as_str()).ok()?;
        cfg[DEFAULT_ADAPTER_KEY] = serde_json::to_value(hci.to_i32()).ok().unwrap();
        serde_json::ser::to_string_pretty(&cfg).ok()
    }) {
        Some(s) => std::fs::write(BTMANAGERD_CONF, s).is_ok(),
        None => false,
    }
}

fn list_hci_devices_string() -> Vec<String> {
    match std::fs::read_dir(HCI_DEVICES_DIR) {
        Ok(entries) => entries
            .map(|e| e.unwrap().path().file_name().unwrap().to_str().unwrap().to_string())
            .collect::<Vec<_>>(),
        _ => Vec::new(),
    }
}

/// Check whether a certain hci device exists in sysfs.
pub fn check_hci_device_exists(hci: RealHciIndex) -> bool {
    Path::new(format!("{}/hci{}", HCI_DEVICES_DIR, hci.to_i32()).as_str()).exists()
}

/// Get the devpath for a given hci index. This gives a stable path that can be
/// used to identify a device even as the hci index fluctuates.
pub fn get_devpath_for_hci(hci: RealHciIndex) -> Option<String> {
    match std::fs::canonicalize(format!("{}/hci{}/device", HCI_DEVICES_DIR, hci.to_i32()).as_str())
    {
        Ok(p) => Some(p.into_os_string().into_string().ok()?),
        Err(e) => {
            log::debug!("Failed to get devpath for {} with error: {}", hci, e);
            None
        }
    }
}

pub fn list_pid_files(pid_dir: &str) -> Vec<String> {
    match std::fs::read_dir(pid_dir) {
        Ok(entries) => entries
            .map(|e| e.unwrap().path().file_name().unwrap().to_str().unwrap().to_string())
            .collect::<Vec<_>>(),
        _ => Vec::new(),
    }
}

/// Calls the reset sysfs entry for an hci device. Returns True if the write succeeds.
pub fn reset_hci_device(hci: RealHciIndex) -> bool {
    let path = format!("/sys/class/bluetooth/hci{}/reset", hci.to_i32());
    std::fs::write(path, "1").is_ok()
}

pub fn read_floss_ll_privacy_enabled() -> std::io::Result<bool> {
    let parent = Path::new(FLOSS_SYSPROPS_OVERRIDE_DIR);
    if !parent.is_dir() {
        return Ok(false);
    }

    let data = std::fs::read_to_string(format!(
        "{}/{}",
        FLOSS_SYSPROPS_OVERRIDE_DIR, "privacy_override.conf"
    ))?;

    Ok(data == "[Sysprops]\nbluetooth.core.gap.le.privacy.enabled=true\n")
}

pub fn write_floss_ll_privacy_enabled(enabled: bool) -> std::io::Result<()> {
    let parent = Path::new(FLOSS_SYSPROPS_OVERRIDE_DIR);

    std::fs::create_dir_all(parent)?;

    let data = format!(
        "[Sysprops]\nbluetooth.core.gap.le.privacy.enabled={}",
        if enabled { "true\n" } else { "false\n" }
    );

    std::fs::write(format!("{}/{}", FLOSS_SYSPROPS_OVERRIDE_DIR, "privacy_override.conf"), data)
}

pub fn set_adapter_coredump_state(enabled: bool) -> std::io::Result<()> {
    let data = format!("{}\n", !enabled as i32);

    for hci in list_hci_devices_string() {
        let path = format!("{}/{}/device/coredump_disabled", HCI_DEVICES_DIR, hci);

        std::fs::write(path, &data)?;
    }

    Ok(())
}

pub fn write_coredump_state_to_file(enabled: bool) -> bool {
    let data = format!("{}\n", !enabled as i32);

    if let Err(e) = std::fs::write(FLOSS_COREDUMP_CONF_PATH, data) {
        log::error!("Failed to write devcoredump state (error: {})", e);
        return false;
    }

    if let Err(e) = set_adapter_coredump_state(enabled) {
        log::error!("Failed to set devcoredump state (error: {})", e);
        return false;
    }

    true
}

#[cfg(test)]
mod tests {
    use super::*;

    fn is_hci_n_enabled_internal_wrapper(config: String, n: i32) -> bool {
        is_hci_n_enabled_internal(config, VirtualHciIndex(n)).unwrap_or(true)
    }

    #[test]
    fn parse_log_level() {
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"error\"}".to_string()).unwrap(),
            LevelFilter::Error
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"warn\"}".to_string()).unwrap(),
            LevelFilter::Warn
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"info\"}".to_string()).unwrap(),
            LevelFilter::Info
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"debug\"}".to_string()).unwrap(),
            LevelFilter::Debug
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"trace\"}".to_string()).unwrap(),
            LevelFilter::Trace
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"random\"}".to_string()).is_none(),
            true
        );
    }

    #[test]
    fn parse_hci0_enabled() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": true}}".to_string(), 0),
            true
        );
    }

    #[test]
    fn modify_hci0_enabled() {
        let modified_string = modify_hci_n_enabled_internal(
            "{\"hci0\":\n{\"enabled\": false}}".to_string(),
            VirtualHciIndex(0),
            true,
        )
        .unwrap();
        assert_eq!(is_hci_n_enabled_internal_wrapper(modified_string, 0), true);
    }

    #[test]
    fn modify_hci0_enabled_from_empty() {
        let modified_string =
            modify_hci_n_enabled_internal("{}".to_string(), VirtualHciIndex(0), true).unwrap();
        assert_eq!(is_hci_n_enabled_internal_wrapper(modified_string, 0), true);
    }

    #[test]
    fn parse_hci0_not_enabled() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": false}}".to_string(), 0),
            false
        );
    }

    #[test]
    fn parse_hci1_not_present() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": true}}".to_string(), 1),
            true
        );
    }
}
