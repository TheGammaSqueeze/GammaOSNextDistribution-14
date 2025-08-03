//! System properties on Android

/// Gets the value of a system property on Android
#[cfg(target_os = "android")]
pub fn get(name: &str) -> Option<String> {
    rustutils::system_properties::read(name).unwrap_or(None)
}

/// Fake getter for non-Android, which will always return nothing.
/// Only added so it compiles & you can conditionally using cfg!
#[cfg(not(target_os = "android"))]
pub fn get(_name: &str) -> Option<String> {
    None
}

/// Gets the specified property as a u32
pub fn get_u32(name: &str) -> Option<u32> {
    if let Some(value) = get(name) {
        value.parse().ok()
    } else {
        None
    }
}

/// Gets the specified property as a bool (logic follows libcutils/properties.cpp)
pub fn get_bool(name: &str) -> Option<bool> {
    if let Some(value) = get(name) {
        match value.as_str() {
            "0" | "n" | "no" | "false" | "off" => Some(false),
            "1" | "y" | "yes" | "true" | "on" => Some(true),
            _ => None,
        }
    } else {
        None
    }
}

/// Gets whether the current build is debuggable
pub fn get_debuggable() -> bool {
    get_bool("ro.debuggable").unwrap_or(false)
}
