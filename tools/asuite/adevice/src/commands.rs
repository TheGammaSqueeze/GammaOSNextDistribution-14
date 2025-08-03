//! Generate and execute adb commands from the host.
use crate::fingerprint::*;
use crate::restart_chooser::{RestartChooser, RestartType};

use std::collections::HashMap;
use std::fmt::Debug;
use std::path::{Path, PathBuf};
use tracing::debug;

#[derive(Clone, Debug, PartialEq)]
pub enum AdbAction {
    /// e.g. adb shell mkdir <device_filename>
    Mkdir,
    /// e.g. adb push <host_filename> <device_filename>
    Push { host_path: String },
    /// e.g. adb shell ln -s <target> <device_filename>
    Symlink { target: String },
    /// e.g. adb rm <device_filename>
    DeleteFile,
    /// e.g. adb rm -rf <device_filename>
    DeleteDir,
}

pub fn split_string(s: &str) -> Vec<String> {
    Vec::from_iter(s.split(' ').map(String::from))
}

/// Given an `action` like AdbAction::Push, return a vector of
/// command line args to pass to `adb`.  `adb` will not be in
/// vector of args.
/// `file_path` is the device-relative file_path.
/// It is expected that the arguments returned arguments will not be
/// evaluated by a shell, but instead passed directly on to `exec`.
/// i.e. If a filename has spaces in it, there should be no quotes.
pub fn command_args(action: &AdbAction, file_path: &Path) -> Vec<String> {
    let path_str = file_path.to_path_buf().into_os_string().into_string().expect("already tested");
    let add_cmd_and_path = |s| {
        let mut args = split_string(s);
        args.push(path_str.clone());
        args
    };
    let add_cmd_and_paths = |s, path: &String| {
        let mut args = split_string(s);
        args.push(path.clone());
        args.push(path_str.clone());
        args
    };
    match action {
        // [adb] mkdir device_path
        AdbAction::Mkdir => add_cmd_and_path("shell mkdir -p"),
        // TODO(rbraunstein): Add compression flag.
        // [adb] push host_path device_path
        AdbAction::Push { host_path } => add_cmd_and_paths("push", host_path),
        // [adb] ln -s -f target device_path
        AdbAction::Symlink { target } => add_cmd_and_paths("shell ln -sf", target),
        // [adb] shell rm device_path
        AdbAction::DeleteFile => add_cmd_and_path("shell rm"),
        // [adb] shell rm -rf device_path
        AdbAction::DeleteDir => add_cmd_and_path("shell rm -rf"),
    }
}

/// Return type for `compute_actions` so we can segregate out deletes.
pub struct Commands {
    pub upserts: HashMap<PathBuf, AdbCommand>,
    pub deletes: HashMap<PathBuf, AdbCommand>,
}

impl Commands {
    pub fn is_empty(&self) -> bool {
        self.upserts.is_empty() && self.deletes.is_empty()
    }
}

/// Compose an adb command, i.e. an argv array, for each action listed in the diffs.
/// e.g. `adb push host_file device_file` or `adb mkdir /path/to/device_dir`
pub fn compose(diffs: &Diffs, product_out: &Path) -> Commands {
    // Note we don't need to make intermediate dirs, adb push
    // will do that for us.

    let mut commands = Commands { upserts: HashMap::new(), deletes: HashMap::new() };

    // We use the same command for updating a file on the device as we do for adding it
    // if it doesn't exist.
    for (file_path, metadata) in diffs.device_needs.iter().chain(diffs.device_diffs.iter()) {
        let host_path =
            product_out.join(file_path).into_os_string().into_string().expect("already visited");
        let adb_cmd = |action| AdbCommand::from_action(action, file_path);
        commands.upserts.insert(
            file_path.to_path_buf(),
            match metadata.file_type {
                FileType::File => adb_cmd(AdbAction::Push { host_path }),
                FileType::Symlink => {
                    adb_cmd(AdbAction::Symlink { target: metadata.symlink.clone() })
                }
                FileType::Directory => adb_cmd(AdbAction::Mkdir),
            },
        );
    }

    for (file_path, metadata) in diffs.device_extra.iter() {
        let adb_cmd = |action| AdbCommand::from_action(action, file_path);
        commands.deletes.insert(
            file_path.to_path_buf(),
            match metadata.file_type {
                // [adb] rm device_path
                FileType::File | FileType::Symlink => adb_cmd(AdbAction::DeleteFile),
                // TODO(rbraunstein): More efficient deletes, or change rm -rf back to rmdir
                FileType::Directory => adb_cmd(AdbAction::DeleteDir),
            },
        );
    }

    commands
}

/// Given a set of files, determine the combined set of commands we need
/// to execute on the device to make the device aware of the new files.
/// In the most conservative case we will return a single "reboot" command.
/// Short of that, there should be zero or one commands per installed file.
/// If multiple installed files are part of the same module, we will reduce
/// to one command for those files.  If multiple services are sync'd, there
/// may be multiple restart commands.
pub fn restart_type(
    build_system: &RestartChooser,
    installed_file_paths: &Vec<String>,
) -> RestartType {
    let mut soft_restart_needed = false;
    let mut reboot_needed = false;

    for installed_file in installed_file_paths {
        let restart_type = build_system.restart_type(installed_file);
        debug!(" -- Restart is {:?} for {}", restart_type.clone(), installed_file);
        match restart_type {
            RestartType::Reboot => reboot_needed = true,
            RestartType::SoftRestart => soft_restart_needed = true,
            RestartType::None => (),
            // TODO(rbraunstein): Deal with determining the command needed. Full reboot for now.
            //RestartType::RestartBinary => (),
        }
    }
    // Note, we don't do early return so we log restart_type for each file.
    if reboot_needed {
        return RestartType::Reboot;
    }
    if soft_restart_needed {
        return RestartType::SoftRestart;
    }
    RestartType::None
}

#[derive(Clone, Debug, PartialEq)]
pub struct AdbCommand {
    /// Args to pass to adb to do the action.
    args: Vec<String>,
    /// Action we are going to perform, like Push, create symlink, delete file.
    pub action: AdbAction,
    /// Device path for file we are operating on.
    pub file: PathBuf,
}

impl AdbCommand {
    /// Pass the command line with spaces between args.
    pub fn from_action(adb_action: AdbAction, device_path: &Path) -> Self {
        AdbCommand {
            args: command_args(&adb_action, device_path),
            action: adb_action,
            file: device_path.to_path_buf(),
        }
    }
    pub fn args(&self) -> Vec<String> {
        self.args.clone()
    }

    pub fn is_mkdir(&self) -> bool {
        matches!(self.action, AdbAction::Mkdir { .. })
    }

    pub fn is_rm(&self) -> bool {
        matches!(self.action, AdbAction::DeleteDir { .. })
            || matches!(self.action, AdbAction::DeleteFile { .. })
    }

    pub fn device_path(&self) -> &Path {
        &self.file
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn push_cmd_args() {
        assert_eq!(
            string_vec(&["push", "local/host/path", "device/path",]),
            command_args(
                &AdbAction::Push { host_path: "local/host/path".to_string() },
                &PathBuf::from("device/path")
            )
        );
    }

    #[test]
    fn mkdir_cmd_args() {
        assert_eq!(
            string_vec(&["shell", "mkdir", "-p", "device/new/dir",]),
            command_args(&AdbAction::Mkdir, &PathBuf::from("device/new/dir"))
        );
    }

    #[test]
    fn symlink_cmd_args() {
        assert_eq!(
            string_vec(&["shell", "ln", "-sf", "the_target", "system/tmp/p",]),
            command_args(
                &AdbAction::Symlink { target: "the_target".to_string() },
                &PathBuf::from("system/tmp/p")
            )
        );
    }
    #[test]
    fn delete_file_cmd_args() {
        assert_eq!(
            string_vec(&["shell", "rm", "system/file.so",]),
            command_args(&AdbAction::DeleteFile, &PathBuf::from("system/file.so"))
        );
    }
    #[test]
    fn delete_dir_cmd_args() {
        assert_eq!(
            string_vec(&["shell", "rm", "-rf", "some/dir"]),
            command_args(&AdbAction::DeleteDir, &PathBuf::from("some/dir"))
        );
    }

    #[test]
    fn cmds_on_files_spaces_utf8_chars_work() {
        // File with spaces in the name
        assert_eq!(
            string_vec(&["push", "local/host/path with space", "device/path with space",]),
            command_args(
                &AdbAction::Push { host_path: "local/host/path with space".to_string() },
                &PathBuf::from("device/path with space")
            )
        );
        // Symlink with spaces and utf8 chars
        assert_eq!(
            string_vec(&["shell", "ln", "-sf", "cup of water", "/tmp/ha ha/물 주세요",]),
            command_args(
                &AdbAction::Symlink { target: "cup of water".to_string() },
                &PathBuf::from("/tmp/ha ha/물 주세요")
            )
        );
    }

    // helper to gofrom vec of str -> vec of String
    fn string_vec(v: &[&str]) -> Vec<String> {
        v.iter().map(|&x| x.into()).collect()
    }
}
