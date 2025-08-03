// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

use std::path::Path;
use std::process::Command;

use anyhow::{bail, ensure, Result};

const LLVM_ANDROID_REL_PATH: &str = "toolchain/llvm_android";

/// Return the Android checkout's current llvm version.
///
/// This uses android_version.get_svn_revision_number, a python function
/// that can't be executed directly. We spawn a Python3 program
/// to run it and get the result from that.
pub fn get_android_llvm_version(android_checkout: &Path) -> Result<String> {
    let mut command = new_android_cmd(android_checkout, "python3")?;
    command.args([
        "-c",
        "import android_version; print(android_version.get_svn_revision_number(), end='')",
    ]);
    let output = command.output()?;
    if !output.status.success() {
        bail!(
            "could not get android llvm version: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }
    let out_string = String::from_utf8(output.stdout)?.trim().to_string();
    Ok(out_string)
}

/// Sort the Android patches using the cherrypick_cl.py Android utility.
///
/// This assumes that:
///   1. There exists a python script called cherrypick_cl.py
///   2. That calling it with the given arguments sorts the PATCHES.json file.
///   3. Calling it does nothing besides sorting the PATCHES.json file.
///
/// We aren't doing our own sorting because we shouldn't have to update patch_sync along
/// with cherrypick_cl.py any time they change the __lt__ implementation.
pub fn sort_android_patches(android_checkout: &Path) -> Result<()> {
    let mut command = new_android_cmd(android_checkout, "python3")?;
    command.args(["cherrypick_cl.py", "--reason", "patch_sync sorting"]);
    let output = command.output()?;
    if !output.status.success() {
        bail!(
            "could not sort: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }
    Ok(())
}

fn new_android_cmd(android_checkout: &Path, cmd: &str) -> Result<Command> {
    let mut command = Command::new(cmd);
    let llvm_android_dir = android_checkout.join(LLVM_ANDROID_REL_PATH);
    ensure!(
        llvm_android_dir.is_dir(),
        "can't make android command; {} is not a directory",
        llvm_android_dir.display()
    );
    command.current_dir(llvm_android_dir);
    Ok(command)
}
