//! Crate to provide the AppClass for an installed file.
/// The current implementation parses module info and creates
/// a map from installed file to its highest ranking "class".
/// Later the AppClass will be mapped to a restart level.
use serde::{Deserialize, Serialize};
use std::ffi::OsStr;
use std::path::Path;

use crate::cli::RestartChoice;

pub struct RestartChooser {
    // Users override for restarting.
    restart_choice: RestartChoice,
}

impl RestartChooser {
    pub fn new(restart_choice: &RestartChoice) -> Self {
        RestartChooser { restart_choice: restart_choice.clone() }
    }

    // Given a file in ANDROID_PRODUCT_OUT tree, return the restart type for it.
    pub fn restart_type(&self, installed_file: &str) -> RestartType {
        match self.restart_choice {
            RestartChoice::Auto => {
                if can_soft_restart_based_on_filename(installed_file) {
                    RestartType::SoftRestart
                } else {
                    RestartType::Reboot
                }
            }
            RestartChoice::None => RestartType::None,
            RestartChoice::Reboot => RestartType::Reboot,
            RestartChoice::Restart => RestartType::SoftRestart,
        }
    }
}

// Some file extensions only need a SoftRestart due to being
// reloaded when zygote restarts on `adb shell start`
// Extensions like xml, prof, bprof come up with .jar files (i.e. framework-minus-apex),
// so we list them here too.
const SOFT_RESTART_FILE_EXTS: &[&str] =
    &["art", "oat", "vdex", "odex", "fsv_meta", "apk", "jar", "xml", "prof", "bprof"];
fn can_soft_restart_based_on_filename(filename: &str) -> bool {
    let ext = Path::new(filename).extension().and_then(OsStr::to_str).unwrap_or("");
    SOFT_RESTART_FILE_EXTS.contains(&ext)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Module {
    pub class: Vec<String>,
    pub installed: Vec<String>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum RestartType {
    /// The device needs to be rebooted
    Reboot,
    /// Adb shell restart will suffice
    SoftRestart,
    /// No restarts needed.
    None,
    // A force kill command will be enough.
    // RestartBinary,
}

#[cfg(test)]
mod tests {

    use super::*;
    fn auto_restart() -> RestartChooser {
        RestartChooser::new(&RestartChoice::Auto)
    }

    #[test]
    fn reboot_for_module_with_shared_and_static_lib() {
        assert_eq!(
            RestartType::Reboot,
            auto_restart().restart_type("vendor/lib64/DefaultVehicleHal.so")
        );
    }

    #[test]
    fn test_so_rebooots() {
        assert_eq!(RestartType::Reboot, auto_restart().restart_type("vendor/lib64/Weird.so"));
    }
    #[test]
    fn test_bogus_file_rebooots() {
        // It doesn't matter if the file exists or not, if the extension doesn't match, it is a reboot.
        assert_eq!(RestartType::Reboot, auto_restart().restart_type("bad/file/path"));
    }

    #[test]
    fn soft_restart_for_certain_file_extensions() {
        // Have extensions in SOFT_RESET_FILE_EXTS
        for installed_file in &[
            "vendor/good/file/path.art",
            "vendor/good/file/path.oat",
            "vendor/good/file/path.vdex",
            "vendor/good/file/path.fsv_meta",
        ] {
            assert_eq!(
                RestartType::SoftRestart,
                auto_restart().restart_type(installed_file),
                "Wrong class for {}",
                installed_file
            );
        }

        // Do NOT have extensions in SOFT_RESET_FILE_EXTS (REBOOT due to module class)
        for installed_file in &[
            "vendor/good/file/path.extraart",
            "vendor/good/file/path.artextra",
            "vendor/good/file/path",
        ] {
            assert_eq!(
                RestartType::Reboot,
                auto_restart().restart_type(installed_file),
                "Wrong class for {}",
                installed_file
            );
        }
    }

    #[test]
    fn binary_with_rc_file_reboots_for_rc() {
        assert_eq!(
            RestartType::Reboot,
            auto_restart().restart_type("system/bin/surfaceflinger.rc")
        );
        // This fails after our choice to reboot based on extension.
        // assert_eq!(
        //     RestartType::SoftRestart,
        //     auto_restart().restart_type("system/bin/surfaceflinger")
        // );
    }

    #[test]
    fn restart_choice_is_used() {
        let restart_chooser = RestartChooser::new(&RestartChoice::None);
        assert_eq!(RestartType::None, restart_chooser.restart_type("system/bin/surfaceflinger.rc"));
    }
}
