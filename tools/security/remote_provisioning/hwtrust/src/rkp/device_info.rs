use anyhow::anyhow;
use hex;
use std::fmt;

#[non_exhaustive]
#[derive(Clone, Eq, PartialEq)]
/// Describes a device that is registered with the RKP backend. This implementation contains fields
/// common to all versions defined in DeviceInfo.aidl.
pub struct DeviceInfo {
    /// Version of this data structure.
    pub version: DeviceInfoVersion,
    /// The device's marketed brand.
    pub brand: String,
    /// The device maker.
    pub manufacturer: String,
    /// A variant of a device. Multiple products may be built off the same device.
    pub product: String,
    /// End-user-visible name of the product.
    pub model: String,
    /// The high-level industrial design. What makes up a "device" is generally hardware
    /// characteristics like form factor, cpu, etc. Multiple products/models may be shipped on
    /// the same underlying device.
    pub device: String,
    /// Verified boot state.
    pub vb_state: Option<DeviceInfoVbState>,
    /// Whether the bootloader is locked or not.
    pub bootloader_state: Option<DeviceInfoBootloaderState>,
    /// Digest of the verified boot metadata structures.
    pub vbmeta_digest: Vec<u8>,
    /// Partner-defined operating system version.
    pub os_version: Option<String>,
    /// Patch level of the system partition.
    pub system_patch_level: u32,
    /// Patch level of the kernel.
    pub boot_patch_level: u32,
    /// Patch level of the vendor partition.
    pub vendor_patch_level: u32,
    /// If backed by KeyMint, this is the security level of the HAL.
    pub security_level: Option<DeviceInfoSecurityLevel>,
    /// Whether or not secure boot is enforced/required by the SoC.
    pub fused: bool,
}

impl fmt::Debug for DeviceInfo {
    fn fmt(&self, fmt: &mut fmt::Formatter) -> fmt::Result {
        let security_level: &dyn fmt::Debug = self.security_level.as_ref().map_or(&"<none>", |s| s);
        let os_version: &dyn fmt::Debug = self.os_version.as_ref().map_or(&"<none>", |v| v);
        let bootloader_state: &dyn fmt::Debug =
            self.bootloader_state.as_ref().map_or(&"<none>", |v| v);
        let vb_state: &dyn fmt::Debug = self.vb_state.as_ref().map_or(&"<none>", |v| v);

        fmt.debug_struct("DeviceInfo")
            .field("version", &self.version)
            .field("brand", &self.brand)
            .field("manufacturer", &self.manufacturer)
            .field("product", &self.product)
            .field("model", &self.model)
            .field("device", &self.device)
            .field("vb_state", vb_state)
            .field("bootloader_state", bootloader_state)
            .field("vbmeta_digest", &hex::encode(&self.vbmeta_digest))
            .field("os_version", os_version)
            .field("system_patch_level", &self.system_patch_level)
            .field("boot_patch_level", &self.boot_patch_level)
            .field("vendor_patch_level", &self.vendor_patch_level)
            .field("security_level", security_level)
            .field("fused", &self.fused)
            .finish()
    }
}

#[derive(Copy, Clone, Debug, Eq, PartialEq)]
/// Whether the bootloader allows unsigned or third-party-signed images.
pub enum DeviceInfoBootloaderState {
    /// The bootloader is locked, and will only allow signed images.
    Locked,
    /// The bootloader will load arbitrary images.
    Unlocked,
}

impl TryFrom<&str> for DeviceInfoBootloaderState {
    type Error = anyhow::Error;

    fn try_from(s: &str) -> Result<Self, Self::Error> {
        match s.to_ascii_lowercase().as_str() {
            "locked" => Ok(Self::Locked),
            "unlocked" => Ok(Self::Unlocked),
            _ => Err(anyhow!("Invalid bootloader state: `{s}`")),
        }
    }
}

#[derive(Copy, Clone, Debug, Eq, PartialEq)]
/// State of the verified boot validation.
pub enum DeviceInfoVbState {
    /// The device booted an OEM-signed image.
    Green,
    /// The device booted an image signed by a key set by the end user.
    Yellow,
    /// The bootloader is unlocked, and no guarantees of image integrity are available.
    Orange,
}

impl TryFrom<&str> for DeviceInfoVbState {
    type Error = anyhow::Error;

    fn try_from(s: &str) -> Result<Self, Self::Error> {
        match s.to_ascii_lowercase().as_str() {
            "green" => Ok(Self::Green),
            "yellow" => Ok(Self::Yellow),
            "orange" => Ok(Self::Orange),
            _ => Err(anyhow!("Invalid VB state: `{s}`")),
        }
    }
}

#[derive(Copy, Clone, Debug, Eq, PartialEq)]
/// The version of the DeviceInfo structure, which may updated with HAL changes.
pub enum DeviceInfoVersion {
    /// First supported version. Prior to this (V1), almost all fields were optional.
    V2,
    /// Explicit version removed from the CBOR. Otherwise identical to V2.
    V3,
}

impl TryFrom<u32> for DeviceInfoVersion {
    type Error = anyhow::Error;

    fn try_from(i: u32) -> Result<Self, Self::Error> {
        match i {
            2 => Ok(Self::V2),
            3 => Ok(Self::V3),
            _ => Err(anyhow!("Invalid version: `{i}`")),
        }
    }
}

#[derive(Copy, Clone, Debug, Eq, PartialEq)]
/// This corresponds with where KeyMint's backing service lives: a TEE or in a separate SE.
pub enum DeviceInfoSecurityLevel {
    /// KeyMint's backend runs in a Trusted Execution Environment.
    Tee,
    /// KeyMint's backend runs in a Secure Element.
    StrongBox,
}

impl TryFrom<&str> for DeviceInfoSecurityLevel {
    type Error = anyhow::Error;

    fn try_from(s: &str) -> Result<Self, Self::Error> {
        match s.to_ascii_lowercase().as_str() {
            "strongbox" => Ok(Self::StrongBox),
            "tee" => Ok(Self::Tee),
            _ => Err(anyhow!("Invalid security level: `{s}`")),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bootloader_state_from_string() {
        assert_eq!(
            DeviceInfoBootloaderState::try_from("LoCkEd").unwrap(),
            DeviceInfoBootloaderState::Locked
        );
        assert_eq!(
            DeviceInfoBootloaderState::try_from("UNLocked").unwrap(),
            DeviceInfoBootloaderState::Unlocked
        );
        DeviceInfoBootloaderState::try_from("nope").unwrap_err();
    }

    #[test]
    fn vb_state_from_string() {
        assert_eq!(DeviceInfoVbState::try_from("greEN").unwrap(), DeviceInfoVbState::Green);
        assert_eq!(DeviceInfoVbState::try_from("YeLLoW").unwrap(), DeviceInfoVbState::Yellow);
        assert_eq!(DeviceInfoVbState::try_from("ORange").unwrap(), DeviceInfoVbState::Orange);
        DeviceInfoVbState::try_from("bad").unwrap_err();
    }

    #[test]
    fn version_from_int() {
        DeviceInfoVersion::try_from(1).unwrap_err();
        assert_eq!(DeviceInfoVersion::try_from(2).unwrap(), DeviceInfoVersion::V2);
        assert_eq!(DeviceInfoVersion::try_from(3).unwrap(), DeviceInfoVersion::V3);
        DeviceInfoVersion::try_from(4).unwrap_err();
    }

    #[test]
    fn security_level_from_string() {
        assert_eq!(
            DeviceInfoSecurityLevel::try_from("StrongBOX").unwrap(),
            DeviceInfoSecurityLevel::StrongBox
        );
        assert_eq!(DeviceInfoSecurityLevel::try_from("TeE").unwrap(), DeviceInfoSecurityLevel::Tee);
        DeviceInfoSecurityLevel::try_from("insecure").unwrap_err();
    }
}
