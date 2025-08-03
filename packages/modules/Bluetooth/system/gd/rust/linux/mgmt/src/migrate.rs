//! The migrate module is intended to make it possible to migrate device
//! information and settings between BlueZ and Floss.
//!
//! The rules for [source] -> [target] migration:
//! - All devices that exist in [source] must exist in [target]. Delete
//!   ones that don't exist in [source].
//! - If the device exists in both [source] and [target], replace [target]
//!   keys with transferred [source] keys, but keep all keys that don't
//!   exist in [source]
//! - Drop devices that run into issues, but continue trying to migrate
//!   all others

use std::collections::HashMap;
use std::fs;
use std::path::Path;

use configparser::ini::Ini;
use glob::glob;

use log::{debug, error, info, warn};

const BT_LIBDIR: &str = "/var/lib/bluetooth";
const FLOSS_CONF_FILE: &str = "/var/lib/bluetooth/bt_config.conf";

const ADAPTER_SECTION_NAME: &str = "Adapter";
const GENERAL_SECTION_NAME: &str = "General";
const LINKKEY_SECTION_NAME: &str = "LinkKey";
const DEVICEID_SECTION_NAME: &str = "DeviceID";
const IRK_SECTION_NAME: &str = "IdentityResolvingKey";
const LTK_SECTION_NAME: &str = "LongTermKey";
const BLUEZ_PERIPHERAL_LTK_SECTION_NAME: &str = "PeripheralLongTermKey";
const BLUEZ_LOCAL_LTK_SECTION_NAME: &str = "SlaveLongTermKey";
const REPORT_MAP_SECTION_NAME: &str = "ReportMap";

const CLASSIC_TYPE: &str = "BR/EDR;";
const LE_TYPE: &str = "LE;";
const DUAL_TYPE: &str = "BR/EDR;LE;";

/// Represents LTK info
#[derive(Debug, Default)]
struct LtkInfo {
    key: u128,
    rand: u64,
    ediv: u16,
    auth: u8,
    len: u8,
}

impl LtkInfo {
    fn new_from_bluez(bluez_conf: &Ini, sec: &str) -> Self {
        LtkInfo {
            key: u128::from_str_radix(bluez_conf.get(sec, "Key").unwrap_or_default().as_str(), 16)
                .unwrap_or_default(),
            rand: bluez_conf
                .get(sec, "Rand")
                .unwrap_or_default()
                .parse::<u64>()
                .unwrap_or_default(),
            ediv: bluez_conf
                .get(sec, "EDiv")
                .unwrap_or_default()
                .parse::<u16>()
                .unwrap_or_default(),
            auth: bluez_conf
                .get(sec, "Authenticated")
                .unwrap_or_default()
                .parse::<u8>()
                .unwrap_or_default(),
            len: bluez_conf
                .get(sec, "EncSize")
                .unwrap_or_default()
                .parse::<u8>()
                .unwrap_or_default(),
        }
    }

    // LE_KEY_PENC = LTK + RAND (64) + EDIV (16) + Security Level (8) + Key Length (8)
    fn try_from_penc(val: String) -> Result<Self, &'static str> {
        if val.len() != 56 {
            return Err("PENC String provided to LtkInfo is not the right size");
        }

        Ok(LtkInfo {
            key: u128::from_str_radix(&val[0..32], 16).unwrap_or_default(),
            rand: u64::from_str_radix(&val[32..48], 16).unwrap_or_default().swap_bytes(),
            ediv: u16::from_str_radix(&val[48..52], 16).unwrap_or_default().swap_bytes(),
            auth: u8::from_str_radix(&val[52..54], 16).unwrap_or_default(),
            len: u8::from_str_radix(&val[54..56], 16).unwrap_or_default(),
        })
    }

    fn try_into_penc(self) -> Result<String, &'static str> {
        Ok(format!(
            "{:032x}{:016x}{:04x}{:02x}{:02x}",
            self.key,
            self.rand.swap_bytes(),
            self.ediv.swap_bytes(),
            self.auth,
            self.len
        ))
    }

    // LE_KEY_LENC = LTK + EDIV (16) + Key Size (8) + Security Level (8)
    fn try_from_lenc(val: String) -> Result<Self, &'static str> {
        if val.len() != 40 {
            return Err("LENC String provided to LtkInfo is not the right size");
        }

        Ok(LtkInfo {
            key: u128::from_str_radix(&val[0..32], 16).unwrap_or_default(),
            ediv: u16::from_str_radix(&val[32..36], 16).unwrap_or_default().swap_bytes(),
            len: u8::from_str_radix(&val[36..38], 16).unwrap_or_default(),
            auth: u8::from_str_radix(&val[38..40], 16).unwrap_or_default(),
            rand: 0,
        })
    }

    fn try_into_lenc(self) -> Result<String, &'static str> {
        Ok(format!(
            "{:032x}{:04x}{:02x}{:02x}",
            self.key,
            self.ediv.swap_bytes(),
            self.len,
            self.auth,
        ))
    }
}

/// Represents the different conversions that can be done on keys
pub enum Converter {
    HexToDec,
    DecToHex,
    Base64ToHex,
    HexToBase64,

    TypeB2F,
    TypeF2B,
    AddrTypeB2F,
    AddrTypeF2B,

    ReverseEndianLowercase,
    ReverseEndianUppercase,

    ReplaceSemiColonWithSpace,
    ReplaceSpaceWithSemiColon,
}

/// Represents the different actions to perform on a DeviceKey
pub enum KeyAction {
    WrapOk,
    Apply(Converter),
    ToSection(&'static str),
    ApplyToSection(Converter, &'static str),
}

pub type DeviceKeyError = String;

/// Represents required info needed to convert keys between Floss and BlueZ
struct DeviceKey {
    pub key: &'static str,
    action: KeyAction,
    // Needed in Floss to BlueZ conversion
    pub section: &'static str,
}

impl DeviceKey {
    /// Returns a DeviceKey with the key and action given
    fn new(key: &'static str, action: KeyAction) -> Self {
        Self { key: key, action: action, section: "" }
    }

    /// Performs the KeyAction stored and returns the result of the key conversion
    fn apply_action(&mut self, value: String) -> Result<String, DeviceKeyError> {
        // Helper function to do the actual conversion
        fn apply_conversion(conv: &Converter, value: String) -> Result<String, DeviceKeyError> {
            match conv {
                Converter::HexToDec => hex_str_to_dec_str(value),
                Converter::DecToHex => dec_str_to_hex_str(value),
                Converter::Base64ToHex => base64_str_to_hex_str(value),
                Converter::HexToBase64 => hex_str_to_base64_str(value),
                Converter::TypeB2F => bluez_to_floss_type(value),
                Converter::TypeF2B => floss_to_bluez_type(value),
                Converter::AddrTypeB2F => bluez_to_floss_addr_type(value),
                Converter::AddrTypeF2B => floss_to_bluez_addr_type(value),
                Converter::ReverseEndianLowercase => reverse_endianness(value, false),
                Converter::ReverseEndianUppercase => reverse_endianness(value, true),
                Converter::ReplaceSemiColonWithSpace => Ok(value.replace(";", " ")),
                Converter::ReplaceSpaceWithSemiColon => Ok(value.replace(" ", ";")),
            }
        }

        match &self.action {
            KeyAction::WrapOk => Ok(value),
            KeyAction::Apply(converter) => apply_conversion(converter, value),
            KeyAction::ToSection(sec) => {
                self.section = sec;
                Ok(value)
            }
            KeyAction::ApplyToSection(converter, sec) => {
                self.section = sec;
                apply_conversion(converter, value)
            }
        }
    }
}

fn hex_str_to_dec_str(str: String) -> Result<String, String> {
    match u32::from_str_radix(str.trim_start_matches("0x"), 16) {
        Ok(str) => Ok(format!("{}", str)),
        Err(err) => Err(format!("Error converting from hex string to dec string: {}", err)),
    }
}

fn dec_str_to_hex_str(str: String) -> Result<String, String> {
    match str.parse::<u32>() {
        Ok(x) => Ok(format!("0x{:X}", x)),
        Err(err) => Err(format!("Error converting from dec string to hex string: {}", err)),
    }
}

fn base64_str_to_hex_str(str: String) -> Result<String, String> {
    match base64::decode(str) {
        Ok(bytes) => {
            let res: String = bytes.iter().map(|b| format!("{:02x}", b)).collect();
            Ok(res)
        }
        Err(err) => Err(format!("Error converting from base64 string to hex string: {}", err)),
    }
}

fn hex_str_to_base64_str(str: String) -> Result<String, String> {
    // Make vector of bytes from octets
    let mut bytes = Vec::new();
    for i in 0..(str.len() / 2) {
        let res = u8::from_str_radix(&str[2 * i..2 * i + 2], 16);
        match res {
            Ok(v) => bytes.push(v),
            Err(err) => {
                return Err(format!("Error converting from hex string to base64 string: {}", err));
            }
        }
    }

    Ok(base64::encode(&bytes))
}

fn bluez_to_floss_type(str: String) -> Result<String, String> {
    match str.as_str() {
        CLASSIC_TYPE => Ok("1".into()),
        LE_TYPE => Ok("2".into()),
        DUAL_TYPE => Ok("3".into()),
        x => Err(format!("Error converting type. Unknown type: {}", x)),
    }
}

fn floss_to_bluez_type(str: String) -> Result<String, String> {
    match str.as_str() {
        "1" => Ok(CLASSIC_TYPE.into()),
        "2" => Ok(LE_TYPE.into()),
        "3" => Ok(DUAL_TYPE.into()),
        x => Err(format!("Error converting type. Unknown type: {}", x)),
    }
}

fn bluez_to_floss_addr_type(str: String) -> Result<String, String> {
    match str.as_str() {
        "public" => Ok("0".into()),
        "static" => Ok("1".into()),
        x => Err(format!("Error converting address type. Unknown type: {}", x)),
    }
}

fn floss_to_bluez_addr_type(str: String) -> Result<String, String> {
    match str.as_str() {
        "0" => Ok("public".into()),
        "1" => Ok("static".into()),
        x => Err(format!("Error converting address type. Unknown type: {}", x)),
    }
}

// BlueZ stores link keys as little endian and Floss as big endian
fn reverse_endianness(str: String, uppercase: bool) -> Result<String, String> {
    match u128::from_str_radix(str.as_str(), 16) {
        Ok(x) => {
            if uppercase {
                Ok(format!("{:0>32X}", x.swap_bytes()))
            } else {
                Ok(format!("{:0>32x}", x.swap_bytes()))
            }
        }
        Err(err) => Err(format!("Error converting link key: {}", err)),
    }
}

/// Helper function that does the conversion from BlueZ to Floss for a single device
///
/// # Arguments
/// * `filename` - A string slice that holds the path of the BlueZ file to get info from
/// * `addr` - A string slice that holds the address of the BlueZ device that we're converting
/// * `floss_conf` - The Floss Ini that we're adding to
/// * `is_hid_file` - Whether the file is a BlueZ hog-uhid-cache file or BlueZ info file
///
/// # Returns
/// Whether the conversion was successful or not
fn convert_from_bluez_device(
    filename: &str,
    addr: &str,
    floss_conf: &mut Ini,
    is_hid_file: bool,
) -> bool {
    // Floss device address strings need to be lower case
    let addr_lower = addr.to_lowercase();

    let mut bluez_conf = Ini::new_cs();
    // Default Ini uses ";" and "#" for comments
    bluez_conf.set_comment_symbols(&['!', '#']);
    let bluez_map = match bluez_conf.load(filename) {
        Ok(map) => map,
        Err(err) => {
            error!(
                "Error converting BlueZ conf to Floss conf: {}. Dropping conversion for device {}",
                err, addr
            );
            floss_conf.remove_section(addr_lower.as_str());
            return false;
        }
    };

    // Floss will not load the HID info unless it sees this key and BlueZ does not have a matching key
    if is_hid_file {
        floss_conf.set(addr_lower.as_str(), "HidAttrMask", Some("0".into()));
    }

    for (sec, props) in bluez_map {
        // Special handling for LE keys since in Floss they are a combination of values in BlueZ
        let handled = match sec.as_str() {
            IRK_SECTION_NAME => {
                // In Floss, LE_KEY_PID = IRK + Identity Address Type (8) + Identity Address
                let irk = reverse_endianness(
                    bluez_conf.get(sec.as_str(), "Key").unwrap_or_default(),
                    false,
                )
                .unwrap_or_default();
                let addr_type = bluez_to_floss_addr_type(
                    bluez_conf.get(GENERAL_SECTION_NAME, "AddressType").unwrap_or_default(),
                )
                .unwrap_or_default()
                .parse::<u8>()
                .unwrap_or_default();
                floss_conf.set(
                    addr_lower.as_str(),
                    "LE_KEY_PID",
                    Some(format!("{}{:02x}{}", irk, addr_type, addr_lower.replace(":", ""))),
                );
                true
            }
            BLUEZ_PERIPHERAL_LTK_SECTION_NAME | LTK_SECTION_NAME => {
                // Special handling since in Floss LE_KEY_PENC is a combination of values in BlueZ
                let ltk: LtkInfo = LtkInfo::new_from_bluez(&bluez_conf, sec.as_str());
                floss_conf.set(
                    addr_lower.as_str(),
                    "LE_KEY_PENC",
                    Some(ltk.try_into_penc().unwrap_or_default()),
                );
                true
            }
            BLUEZ_LOCAL_LTK_SECTION_NAME => {
                // Special handling since in Floss LE_KEY_LENC is a combination of values in BlueZ
                let lltk: LtkInfo = LtkInfo::new_from_bluez(&bluez_conf, sec.as_str());
                floss_conf.set(
                    addr_lower.as_str(),
                    "LE_KEY_LENC",
                    Some(lltk.try_into_lenc().unwrap_or_default()),
                );
                true
            }
            _ => false,
        };

        if handled {
            continue;
        }

        let mut map: HashMap<&str, Vec<DeviceKey>> = if is_hid_file {
            match sec.as_str() {
                REPORT_MAP_SECTION_NAME => [(
                    "report_map",
                    vec![DeviceKey::new("HidDescriptor", KeyAction::Apply(Converter::Base64ToHex))],
                )]
                .into(),
                GENERAL_SECTION_NAME => [
                    ("bcdhid", vec![DeviceKey::new("HidVersion", KeyAction::WrapOk)]),
                    ("bcountrycode", vec![DeviceKey::new("HidCountryCode", KeyAction::WrapOk)]),
                ]
                .into(),
                _ => [].into(),
            }
        } else {
            // info file
            match sec.as_str() {
                GENERAL_SECTION_NAME => [
                    ("Name", vec![DeviceKey::new("Name", KeyAction::WrapOk)]),
                    (
                        "Class",
                        vec![DeviceKey::new("DevClass", KeyAction::Apply(Converter::HexToDec))],
                    ),
                    (
                        "Appearance",
                        vec![DeviceKey::new("Appearance", KeyAction::Apply(Converter::HexToDec))],
                    ),
                    (
                        "SupportedTechnologies",
                        vec![DeviceKey::new("DevType", KeyAction::Apply(Converter::TypeB2F))],
                    ),
                    (
                        "Services",
                        vec![DeviceKey::new(
                            "Service",
                            KeyAction::Apply(Converter::ReplaceSemiColonWithSpace),
                        )],
                    ),
                    (
                        "AddressType",
                        vec![DeviceKey::new("AddrType", KeyAction::Apply(Converter::AddrTypeB2F))],
                    ),
                ]
                .into(),
                LINKKEY_SECTION_NAME => [
                    (
                        "Key",
                        vec![DeviceKey::new(
                            "LinkKey",
                            KeyAction::Apply(Converter::ReverseEndianLowercase),
                        )],
                    ),
                    ("Type", vec![DeviceKey::new("LinkKeyType", KeyAction::WrapOk)]),
                    ("PINLength", vec![DeviceKey::new("PinLength", KeyAction::WrapOk)]),
                ]
                .into(),
                DEVICEID_SECTION_NAME => [
                    (
                        "Source",
                        vec![
                            DeviceKey::new("SdpDiVendorIdSource", KeyAction::WrapOk),
                            DeviceKey::new("VendorIdSource", KeyAction::WrapOk),
                        ],
                    ),
                    (
                        "Vendor",
                        vec![
                            DeviceKey::new("SdpDiManufacturer", KeyAction::WrapOk),
                            DeviceKey::new("VendorId", KeyAction::WrapOk),
                        ],
                    ),
                    (
                        "Product",
                        vec![
                            DeviceKey::new("SdpDiModel", KeyAction::WrapOk),
                            DeviceKey::new("ProductId", KeyAction::WrapOk),
                        ],
                    ),
                    (
                        "Version",
                        vec![
                            DeviceKey::new("SdpDiHardwareVersion", KeyAction::WrapOk),
                            DeviceKey::new("ProductVersion", KeyAction::WrapOk),
                        ],
                    ),
                ]
                .into(),
                _ => [].into(),
            }
        };

        // Do the conversion for all keys found in BlueZ
        for (k, v) in props {
            match map.get_mut(k.as_str()) {
                Some(keys) => {
                    for key in keys {
                        let new_val = match key.apply_action(v.clone().unwrap_or_default()) {
                            Ok(val) => val,
                            Err(err) => {
                                error!(
                                    "Error converting BlueZ conf to Floss conf: {}. \
                                        Dropping conversion for device {}",
                                    err, addr
                                );
                                floss_conf.remove_section(addr_lower.as_str());
                                return false;
                            }
                        };
                        floss_conf.set(addr_lower.as_str(), key.key, Some(new_val));
                    }
                }
                None => {
                    debug!("No key match: {}", k);
                }
            }
        }
    }

    true
}

/// This is the main function that handles the device migration from BlueZ to Floss.
pub fn migrate_bluez_devices() {
    // Maps adapter address to Ini
    let mut adapter_conf_map: HashMap<String, Ini> = HashMap::new();

    // Find and parse all device files
    // In BlueZ, device info files look like /var/lib/bluetooth/<adapter address>/<device address>/info
    let globbed = match glob(format!("{}/*:*/*:*/info", BT_LIBDIR).as_str()) {
        Ok(v) => v,
        Err(_) => {
            warn!("Didn't find any BlueZ adapters to migrate");
            return;
        }
    };
    for entry in globbed {
        let info_path = entry.unwrap_or_default();
        let hid_path = info_path.to_str().unwrap_or_default().replace("info", "hog-uhid-cache");
        let addrs = info_path.to_str().unwrap_or_default().split('/').collect::<Vec<&str>>();
        let adapter_addr = addrs[addrs.len() - 3];
        let device_addr = addrs[addrs.len() - 2];
        // Create new Ini file if it doesn't already exist
        adapter_conf_map.entry(adapter_addr.into()).or_insert(Ini::new_cs());
        if !convert_from_bluez_device(
            info_path.to_str().unwrap_or_default(),
            device_addr,
            adapter_conf_map.get_mut(adapter_addr).unwrap_or(&mut Ini::new_cs()),
            /*is_hid_file=*/ false,
        ) {
            continue;
        }

        // Check if we have HID info
        if Path::new(hid_path.as_str()).exists() {
            convert_from_bluez_device(
                hid_path.as_str(),
                device_addr,
                adapter_conf_map.get_mut(adapter_addr).unwrap_or(&mut Ini::new_cs()),
                /*is_hid_file=*/ true,
            );
        }
    }

    // Write migration to appropriate adapter files
    // TODO(b/232138101): Update for multi-adapter support
    for (adapter, conf) in adapter_conf_map.iter_mut() {
        let mut existing_conf = Ini::new_cs();
        match existing_conf.load(FLOSS_CONF_FILE) {
            Ok(ini) => {
                let devices = conf.sections();
                for (sec, props) in ini {
                    // Drop devices that don't exist in BlueZ
                    if sec.contains(":") && !devices.contains(&sec) {
                        info!("Dropping a device in Floss that doesn't exist in BlueZ");
                        continue;
                    }
                    // Keep keys that weren't transferrable
                    for (k, v) in props {
                        if conf.get(sec.as_str(), k.as_str()) == None {
                            conf.set(sec.as_str(), k.as_str(), v);
                        }
                    }
                }
            }
            // Conf file doesn't exist yet
            Err(_) => {
                conf.set(ADAPTER_SECTION_NAME, "Address", Some(adapter.clone()));
            }
        }
        // Write contents to file
        match conf.write(FLOSS_CONF_FILE) {
            Ok(_) => {
                info!("Successfully migrated devices from BlueZ to Floss for adapter {}", adapter);
            }
            Err(err) => {
                error!(
                    "Error migrating devices from BlueZ to Floss for adapter {}: {}",
                    adapter, err
                );
            }
        }
    }
}

/// Helper function in Floss to BlueZ conversion that takes a Floss device that already
/// exists in BlueZ and keeps keys that weren't available from Floss conf file. Then
/// writes to BlueZ file to complete device migration.
///
/// # Arguments
/// * `filepath` - A string that holds the path of the BlueZ info file
/// * `conf` - BlueZ Ini file that contains migrated Floss device
fn merge_and_write_bluez_conf(filepath: String, conf: &mut Ini) {
    let mut existing_conf = Ini::new_cs();
    existing_conf.set_comment_symbols(&['!', '#']);
    match existing_conf.load(filepath.clone()) {
        // Device already exists in BlueZ
        Ok(ini) => {
            for (sec, props) in ini {
                // Keep keys that weren't transferrable
                for (k, v) in props {
                    if conf.get(sec.as_str(), k.as_str()) == None {
                        conf.set(sec.as_str(), k.as_str(), v);
                    }
                }
            }
        }
        Err(_) => {}
    }
    // Write BlueZ file
    match conf.write(filepath.clone()) {
        Ok(_) => {
            info!("Successfully migrated Floss to BlueZ: {}", filepath);
        }
        Err(err) => {
            error!("Error writing Floss to BlueZ: {}: {}", filepath, err);
        }
    }
}

/// Helper function that does the conversion from Floss to BlueZ for a single adapter
///
/// # Arguments
/// * `filename` - A string slice that holds the path of the Floss conf file to get device info from
fn convert_floss_conf(filename: &str) {
    let mut floss_conf = Ini::new_cs();
    let floss_map = match floss_conf.load(filename) {
        Ok(map) => map,
        Err(err) => {
            warn!(
                "Error opening ini file while converting Floss to BlueZ for {}: {}",
                filename, err
            );
            return;
        }
    };

    let adapter_addr = match floss_conf.get(ADAPTER_SECTION_NAME, "Address") {
        Some(addr) => addr.to_uppercase(),
        None => {
            warn!("No adapter address during Floss to BlueZ migration in {}", filename);
            return;
        }
    };

    // BlueZ info file map
    let mut info_map: HashMap<&str, DeviceKey> = [
        // General
        ("Name", DeviceKey::new("Name", KeyAction::ToSection(GENERAL_SECTION_NAME))),
        (
            "DevClass",
            DeviceKey::new(
                "Class",
                KeyAction::ApplyToSection(Converter::DecToHex, GENERAL_SECTION_NAME),
            ),
        ),
        (
            "Appearance",
            DeviceKey::new(
                "Appearance",
                KeyAction::ApplyToSection(Converter::DecToHex, GENERAL_SECTION_NAME),
            ),
        ),
        (
            "DevType",
            DeviceKey::new(
                "SupportedTechnologies",
                KeyAction::ApplyToSection(Converter::TypeF2B, GENERAL_SECTION_NAME),
            ),
        ),
        (
            "Service",
            DeviceKey::new(
                "Services",
                KeyAction::ApplyToSection(
                    Converter::ReplaceSpaceWithSemiColon,
                    GENERAL_SECTION_NAME,
                ),
            ),
        ),
        (
            "AddrType",
            DeviceKey::new(
                "AddressType",
                KeyAction::ApplyToSection(Converter::AddrTypeF2B, GENERAL_SECTION_NAME),
            ),
        ),
        // LinkKey
        (
            "LinkKey",
            DeviceKey::new(
                "Key",
                KeyAction::ApplyToSection(Converter::ReverseEndianUppercase, LINKKEY_SECTION_NAME),
            ),
        ),
        ("LinkKeyType", DeviceKey::new("Type", KeyAction::ToSection(LINKKEY_SECTION_NAME))),
        ("PinLength", DeviceKey::new("PINLength", KeyAction::ToSection(LINKKEY_SECTION_NAME))),
        // DeviceID
        ("VendorIdSource", DeviceKey::new("Source", KeyAction::ToSection(DEVICEID_SECTION_NAME))),
        ("VendorId", DeviceKey::new("Vendor", KeyAction::ToSection(DEVICEID_SECTION_NAME))),
        ("ProductId", DeviceKey::new("Product", KeyAction::ToSection(DEVICEID_SECTION_NAME))),
        ("ProductVersion", DeviceKey::new("Version", KeyAction::ToSection(DEVICEID_SECTION_NAME))),
    ]
    .into();

    // BlueZ hog-uhid-cache file map
    let mut hid_map: HashMap<&str, DeviceKey> = [
        // General
        ("HidVersion", DeviceKey::new("bcdhid", KeyAction::ToSection(GENERAL_SECTION_NAME))),
        (
            "HidCountryCode",
            DeviceKey::new("bcountrycode", KeyAction::ToSection(GENERAL_SECTION_NAME)),
        ),
        // ReportMap
        (
            "HidDescriptor",
            DeviceKey::new(
                "report_map",
                KeyAction::ApplyToSection(Converter::HexToBase64, REPORT_MAP_SECTION_NAME),
            ),
        ),
    ]
    .into();

    let mut devices: Vec<String> = Vec::new();
    for (sec, props) in floss_map {
        // Skip all the non-adapter sections
        if !sec.contains(":") {
            continue;
        }
        // Keep track of Floss devices we've seen so we can remove BlueZ devices that don't exist on Floss
        devices.push(sec.clone());
        let mut device_addr = sec.to_uppercase();
        let mut bluez_info = Ini::new_cs();
        let mut bluez_hid = Ini::new_cs();
        let mut is_hid: bool = false;
        let has_local_keys: bool = props.contains_key("LE_KEY_LENC");
        for (k, v) in props {
            // Special handling since in Floss LE_KEY_* are a combination of values in BlueZ
            if k == "LE_KEY_PID" {
                // In Floss, LE_KEY_PID = IRK (128) + Identity Address Type (8) + Identity Address (48)
                // Identity Address Type is also found as "AddrType" key so no need to parse it here.
                let val = v.unwrap_or_default();
                if val.len() != 46 {
                    warn!("LE_KEY_PID is not the correct size: {}", val);
                    continue;
                }
                bluez_info.set(
                    IRK_SECTION_NAME,
                    "Key",
                    Some(reverse_endianness(val[0..32].to_string(), true).unwrap_or_default()),
                );
                // BlueZ uses the identity address as the device path
                devices.retain(|d| *d != device_addr);
                device_addr = format!(
                    "{}:{}:{}:{}:{}:{}",
                    &val[34..36],
                    &val[36..38],
                    &val[38..40],
                    &val[40..42],
                    &val[42..44],
                    &val[44..46]
                )
                .to_uppercase();
                devices.push(device_addr.clone().to_lowercase());
                continue;
            } else if k == "LE_KEY_PENC" {
                let ltk = LtkInfo::try_from_penc(v.unwrap_or_default()).unwrap_or_default();
                let section_name = if has_local_keys {
                    BLUEZ_PERIPHERAL_LTK_SECTION_NAME
                } else {
                    LTK_SECTION_NAME
                };
                bluez_info.set(section_name, "Key", Some(format!("{:032X}", ltk.key)));
                bluez_info.set(section_name, "Rand", Some(format!("{}", ltk.rand)));
                bluez_info.set(section_name, "EDiv", Some(format!("{}", ltk.ediv)));
                bluez_info.set(section_name, "Authenticated", Some(format!("{}", ltk.auth)));
                bluez_info.set(section_name, "EncSize", Some(format!("{}", ltk.len)));
                continue;
            } else if k == "LE_KEY_LENC" {
                let ltk = LtkInfo::try_from_lenc(v.unwrap_or_default()).unwrap_or_default();
                bluez_info.set(
                    BLUEZ_LOCAL_LTK_SECTION_NAME,
                    "Key",
                    Some(format!("{:032X}", ltk.key)),
                );
                bluez_info.set(BLUEZ_LOCAL_LTK_SECTION_NAME, "Rand", Some(format!("{}", ltk.rand)));
                bluez_info.set(BLUEZ_LOCAL_LTK_SECTION_NAME, "EDiv", Some(format!("{}", ltk.ediv)));
                bluez_info.set(
                    BLUEZ_LOCAL_LTK_SECTION_NAME,
                    "Authenticated",
                    Some(format!("{}", ltk.auth)),
                );
                bluez_info.set(
                    BLUEZ_LOCAL_LTK_SECTION_NAME,
                    "EncSize",
                    Some(format!("{}", ltk.len)),
                );
                continue;
            }
            // Convert matching info file keys
            match info_map.get_mut(k.as_str()) {
                Some(key) => {
                    let new_val = match key.apply_action(v.unwrap_or_default()) {
                        Ok(val) => val,
                        Err(err) => {
                            warn!("Error converting Floss to Bluez key for adapter {}, device {}, key {}: {}", adapter_addr, device_addr, k, err);
                            continue;
                        }
                    };
                    bluez_info.set(key.section, key.key, Some(new_val));
                    continue;
                }
                None => {
                    debug!("No key match: {}", k)
                }
            }
            // Convert matching hog-uhid-cache file keys
            match hid_map.get_mut(k.as_str()) {
                Some(key) => {
                    is_hid = true;
                    let new_val = match key.apply_action(v.unwrap_or_default()) {
                        Ok(val) => val,
                        Err(err) => {
                            warn!("Error converting Floss to Bluez key for adapter {}, device {}, key {}: {}", adapter_addr, device_addr, k, err);
                            continue;
                        }
                    };
                    bluez_hid.set(key.section, key.key, Some(new_val));
                }
                None => {
                    debug!("No key match: {}", k)
                }
            }
        }

        let path = format!("{}/{}/{}", BT_LIBDIR, adapter_addr, device_addr);

        // Create BlueZ device dir and all its parents if they're missing
        match fs::create_dir_all(path.clone()) {
            Ok(_) => (),
            Err(err) => {
                error!("Error creating dirs during Floss to BlueZ device migration for adapter{}, device {}: {}", adapter_addr, device_addr, err);
            }
        }
        // Write info file
        merge_and_write_bluez_conf(format!("{}/{}", path, "info"), &mut bluez_info);

        // Write hog-uhid-cache file
        if is_hid {
            merge_and_write_bluez_conf(format!("{}/{}", path, "hog-uhid-cache"), &mut bluez_hid);
        }
    }

    // Delete devices that exist in BlueZ but not in Floss
    match glob(format!("{}/{}/*:*", BT_LIBDIR, adapter_addr).as_str()) {
        Ok(globbed) => {
            for entry in globbed {
                let pathbuf = entry.unwrap_or_default();
                let addrs = pathbuf.to_str().unwrap_or_default().split('/').collect::<Vec<&str>>();
                let device_addr: String = addrs[addrs.len() - 1].into();
                if !devices.contains(&device_addr.to_lowercase()) {
                    match fs::remove_dir_all(pathbuf) {
                        Ok(_) => (),
                        Err(err) => {
                            warn!(
                                "Error removing {} during Floss to BlueZ device migration: {}",
                                device_addr, err
                            );
                        }
                    }
                }
            }
        }
        _ => (),
    }
}

/// This is the main function that handles the device migration from Floss to BlueZ.
pub fn migrate_floss_devices() {
    // Find and parse all Floss conf files
    // TODO(b/232138101): Currently Floss only supports a single adapter; update here for multi-adapter support
    let globbed = match glob(FLOSS_CONF_FILE) {
        Ok(v) => v,
        Err(_) => {
            warn!("Didn't find Floss conf file to migrate");
            return;
        }
    };

    for entry in globbed {
        convert_floss_conf(entry.unwrap_or_default().to_str().unwrap_or_default());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_device_key_wrapok() {
        let test_str = String::from("do_nothing");
        let mut key = DeviceKey::new("", KeyAction::WrapOk);
        assert_eq!(key.apply_action(test_str.clone()), Ok(test_str));
    }

    #[test]
    fn test_device_key_to_section() {
        let test_str = String::from("do_nothing");
        let mut key = DeviceKey::new("", KeyAction::ToSection(LINKKEY_SECTION_NAME));
        assert_eq!(key.apply_action(test_str.clone()), Ok(test_str));
        assert_eq!(key.section, LINKKEY_SECTION_NAME)
    }

    #[test]
    fn test_device_key_apply_dec_to_hex() {
        // DevClass example
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::DecToHex));
        assert_eq!(key.apply_action("2360344".to_string()), Ok("0x240418".to_string()));
        assert_eq!(
            key.apply_action("236034B".to_string()),
            Err("Error converting from dec string to hex string: invalid digit found in string"
                .to_string())
        );
    }

    #[test]
    fn test_device_key_apply_to_section_hex_to_dec() {
        // DevClass example
        let mut key = DeviceKey::new(
            "",
            KeyAction::ApplyToSection(Converter::HexToDec, GENERAL_SECTION_NAME),
        );
        assert_eq!(key.apply_action("0x240418".to_string()), Ok("2360344".to_string()));
        assert_eq!(key.section, GENERAL_SECTION_NAME);
        assert_eq!(
            key.apply_action("236034T".to_string()),
            Err("Error converting from hex string to dec string: invalid digit found in string"
                .to_string())
        );
    }

    #[test]
    fn test_hex_to_base64() {
        // HID report map example taken from real HID mouse conversion
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::HexToBase64));
        assert_eq!(
            key.apply_action("05010906a1018501050719e029e71500250175019508810295067508150026a400050719002aa4008100c005010902a10185020901a1009510750115002501050919012910810205011601f826ff07750c95020930093181061581257f75089501093881069501050c0a38028106c0c00643ff0a0202a101851175089513150026ff000902810009029100c0".to_string()),
            Ok("BQEJBqEBhQEFBxngKecVACUBdQGVCIEClQZ1CBUAJqQABQcZACqkAIEAwAUBCQKhAYUCCQGhAJUQdQEVACUBBQkZASkQgQIFARYB+Cb/B3UMlQIJMAkxgQYVgSV/dQiVAQk4gQaVAQUMCjgCgQbAwAZD/woCAqEBhRF1CJUTFQAm/wAJAoEACQKRAMA=".to_string())
        );
        assert_eq!(
            key.apply_action("x5010906a1018501050719e029e71500250175019508810295067508150026a400050719002aa4008100c005010902a10185020901a1009510750115002501050919012910810205011601f826ff07750c95020930093181061581257f75089501093881069501050c0a38028106c0c00643ff0a0202a101851175089513150026ff000902810009029100c0".to_string()),
            Err("Error converting from hex string to base64 string: invalid digit found in string".to_string())
        );
    }

    #[test]
    fn test_hex_to_base64_to_hex() {
        // HID report map example taken from real HID mouse conversion
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::Base64ToHex));
        assert_eq!(
            key.apply_action("BQEJBqEBhQEFBxngKecVACUBdQGVCIEClQZ1CBUAJqQABQcZACqkAIEAwAUBCQKhAYUCCQGhAJUQdQEVACUBBQkZASkQgQIFARYB+Cb/B3UMlQIJMAkxgQYVgSV/dQiVAQk4gQaVAQUMCjgCgQbAwAZD/woCAqEBhRF1CJUTFQAm/wAJAoEACQKRAMA=".to_string()),
            Ok("05010906a1018501050719e029e71500250175019508810295067508150026a400050719002aa4008100c005010902a10185020901a1009510750115002501050919012910810205011601f826ff07750c95020930093181061581257f75089501093881069501050c0a38028106c0c00643ff0a0202a101851175089513150026ff000902810009029100c0".to_string())
        );
        assert_eq!(
            key.apply_action("!BQEJBqEBhQEFBxngKecVACUBdQGVCIEClQZ1CBUAJqQABQcZACqkAIEAwAUBCQKhAYUCCQGhAJUQdQEVACUBBQkZASkQgQIFARYB+Cb/B3UMlQIJMAkxgQYVgSV/dQiVAQk4gQaVAQUMCjgCgQbAwAZD/woCAqEBhRF1CJUTFQAm/wAJAoEACQKRAMA=".to_string()),
            Err("Error converting from base64 string to hex string: Encoded text cannot have a 6-bit remainder.".to_string())
        );
    }

    #[test]
    fn test_typeb2f() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::TypeB2F));
        assert_eq!(key.apply_action(CLASSIC_TYPE.to_string()), Ok("1".to_string()));
        assert_eq!(key.apply_action(LE_TYPE.to_string()), Ok("2".to_string()));
        assert_eq!(key.apply_action(DUAL_TYPE.to_string()), Ok("3".to_string()));
        assert_eq!(
            key.apply_action("FAKE_TYPE".to_string()),
            Err("Error converting type. Unknown type: FAKE_TYPE".to_string())
        );
    }

    #[test]
    fn test_typef2b() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::TypeF2B));
        assert_eq!(key.apply_action("1".to_string()), Ok(CLASSIC_TYPE.to_string()));
        assert_eq!(key.apply_action("2".to_string()), Ok(LE_TYPE.to_string()));
        assert_eq!(key.apply_action("3".to_string()), Ok(DUAL_TYPE.to_string()));
        assert_eq!(
            key.apply_action("FAKE_TYPE".to_string()),
            Err("Error converting type. Unknown type: FAKE_TYPE".to_string())
        );
    }

    #[test]
    fn test_addrtypeb2f() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::AddrTypeB2F));
        assert_eq!(key.apply_action("public".to_string()), Ok("0".to_string()));
        assert_eq!(key.apply_action("static".to_string()), Ok("1".to_string()));
        assert_eq!(
            key.apply_action("FAKE_TYPE".to_string()),
            Err("Error converting address type. Unknown type: FAKE_TYPE".to_string())
        );
    }

    #[test]
    fn test_addrtypef2b() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::AddrTypeF2B));
        assert_eq!(key.apply_action("0".to_string()), Ok("public".to_string()));
        assert_eq!(key.apply_action("1".to_string()), Ok("static".to_string()));
        assert_eq!(
            key.apply_action("FAKE_TYPE".to_string()),
            Err("Error converting address type. Unknown type: FAKE_TYPE".to_string())
        );
    }

    #[test]
    fn test_reverseendian() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::ReverseEndianLowercase));
        assert_eq!(
            key.apply_action("00112233445566778899AABBCCDDEEFF".to_string()),
            Ok("ffeeddccbbaa99887766554433221100".to_string())
        );
        // Link key too small shouldn't panic
        assert_eq!(
            key.apply_action("00112233445566778899AABBCCDDEE".to_string()),
            Ok("eeddccbbaa9988776655443322110000".to_string())
        );
        // Conversion shouldn't lose leading zeros
        assert_eq!(
            key.apply_action("112233445566778899AABBCCDDEE0000".to_string()),
            Ok("0000eeddccbbaa998877665544332211".to_string())
        );
    }

    #[test]
    fn test_replacespacewithsemicolon() {
        // UUID example
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::ReplaceSpaceWithSemiColon));
        assert_eq!(
            key.apply_action(
                "00001800-0000-1000-8000-00805f9b34fb 00001801-0000-1000-8000-00805f9b34fb "
                    .to_string()
            ),
            Ok("00001800-0000-1000-8000-00805f9b34fb;00001801-0000-1000-8000-00805f9b34fb;"
                .to_string())
        );
    }

    #[test]
    fn test_replacesemicolonwithspace() {
        // UUID example
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::ReplaceSemiColonWithSpace));
        assert_eq!(
            key.apply_action(
                "00001800-0000-1000-8000-00805f9b34fb;00001801-0000-1000-8000-00805f9b34fb;"
                    .to_string()
            ),
            Ok("00001800-0000-1000-8000-00805f9b34fb 00001801-0000-1000-8000-00805f9b34fb "
                .to_string())
        );
    }

    #[test]
    fn test_irk_conversion() {
        let mut key = DeviceKey::new("", KeyAction::Apply(Converter::ReverseEndianUppercase));
        assert_eq!(
            key.apply_action("d584da72ceccfdf462405b558441ed44".to_string()),
            Ok("44ED4184555B4062F4FDCCCE72DA84D5".to_string())
        );
        assert_eq!(
            key.apply_action("td584da72ceccfdf462405b558441ed44".to_string()),
            Err("Error converting link key: invalid digit found in string".to_string())
        );
    }

    #[test]
    fn test_ltk_conversion() {
        let floss_penc_key =
            String::from("48fdc93d776cd8cc918f31e422ece00d2322924fa9a09fb30eb20110");
        let pltk = LtkInfo::try_from_penc(floss_penc_key).unwrap_or_default();
        assert_eq!(pltk.key, 0x48FDC93D776CD8CC918F31E422ECE00D);
        assert_eq!(pltk.rand, 12943240503130989091);
        assert_eq!(pltk.ediv, 45582);
        assert_eq!(pltk.auth, 1);
        assert_eq!(pltk.len, 16);
        assert_eq!(
            LtkInfo::try_from_penc(
                "48fdc93d776cd8cc918f31e422ece00d2322924fa9a09fb30eb2011".to_string()
            )
            .unwrap_err(),
            "PENC String provided to LtkInfo is not the right size"
        );

        let floss_lenc_key = String::from("07a104a6d2b464d74ff7e2e80b20295600001001");
        let lltk = LtkInfo::try_from_lenc(floss_lenc_key).unwrap_or_default();
        assert_eq!(lltk.key, 0x07A104A6D2B464D74FF7E2E80B202956);
        assert_eq!(lltk.rand, 0);
        assert_eq!(lltk.ediv, 0);
        assert_eq!(lltk.auth, 1);
        assert_eq!(lltk.len, 16);
        assert_eq!(
            LtkInfo::try_from_lenc("07a104a6d2b464d74ff7e2e80b2029560000100".to_string())
                .unwrap_err(),
            "LENC String provided to LtkInfo is not the right size"
        );
    }

    #[test]
    fn test_convert_from_bluez_device() {
        let test_addr = "00:11:22:33:44:55";
        let mut conf = Ini::new_cs();
        assert_eq!(
            convert_from_bluez_device(
                "test/migrate/fake_bluez_info.toml",
                test_addr,
                &mut conf,
                false
            ),
            true
        );
        assert_eq!(
            convert_from_bluez_device(
                "test/migrate/fake_bluez_hid.toml",
                test_addr,
                &mut conf,
                true
            ),
            true
        );

        assert_eq!(conf.get(test_addr, "Name"), Some(String::from("Test Device")));
        assert_eq!(conf.get(test_addr, "DevClass"), Some(String::from("2360344")));
        assert_eq!(conf.get(test_addr, "Appearance"), Some(String::from("962")));
        assert_eq!(conf.get(test_addr, "DevType"), Some(String::from("1")));
        assert_eq!(
            conf.get(test_addr, "Service"),
            Some(String::from(
                "0000110b-0000-1000-8000-00805f9b34fb 0000110c-0000-1000-8000-00805f9b34fb "
            ))
        );
        assert_eq!(conf.get(test_addr, "AddrType"), Some(String::from("1")));

        assert_eq!(
            conf.get(test_addr, "LinkKey"),
            Some(String::from("ffeeddccbbaa99887766554433221100"))
        );
        assert_eq!(conf.get(test_addr, "LinkKeyType"), Some(String::from("4")));
        assert_eq!(conf.get(test_addr, "PinLength"), Some(String::from("0")));

        assert_eq!(conf.get(test_addr, "SdpDiVendorIdSource"), Some(String::from("1")));
        assert_eq!(conf.get(test_addr, "SdpDiManufacturer"), Some(String::from("100")));
        assert_eq!(conf.get(test_addr, "SdpDiModel"), Some(String::from("22222")));
        assert_eq!(conf.get(test_addr, "SdpDiHardwareVersion"), Some(String::from("3")));

        assert_eq!(conf.get(test_addr, "VendorIdSource"), Some(String::from("1")));
        assert_eq!(conf.get(test_addr, "VendorId"), Some(String::from("100")));
        assert_eq!(conf.get(test_addr, "ProductId"), Some(String::from("22222")));
        assert_eq!(conf.get(test_addr, "ProductVersion"), Some(String::from("3")));

        assert_eq!(
            conf.get(test_addr, "LE_KEY_PID"),
            Some(String::from("ffeeddccbbaa9988776655443322110001001122334455"))
        );
        assert_eq!(
            conf.get(test_addr, "LE_KEY_PENC"),
            Some(String::from("00112233445566778899aabbccddeeff8877665544332211bbaa0110"))
        );

        assert_eq!(conf.get(test_addr, "HidAttrMask"), Some(String::from("0")));
        assert_eq!(
            conf.get(test_addr, "HidDescriptor"),
            Some(String::from("05010906a1018501050719e029e7150025017501950881029505050819012905910295017503910195067508150026a400050719002aa4008100c005010902a10185020901a1009510750115002501050919012910810205011601f826ff07750c95020930093181061581257f75089501093881069501050c0a38028106c0c0050c0901a1018503751095021501268c0219012a8c028160c00643ff0a0202a101851175089513150026ff000902810009029100c0"))
        );
        assert_eq!(conf.get(test_addr, "HidVersion"), Some(String::from("273")));
        assert_eq!(conf.get(test_addr, "HidCountryCode"), Some(String::from("3")));
    }
}
