mod common;

use adevice::adevice::Profiler;
use adevice::cli::RestartChoice;
use adevice::cli::Wait;
use adevice::commands::{AdbAction, AdbCommand};
use adevice::restart_chooser::RestartChooser;
use anyhow::Result;
use common::fakes::FakeDevice;
use std::collections::HashMap;
use std::path::PathBuf;

// Just placeholder for now to show we can call adevice.

fn call_update_with_reboot(wait: Wait) -> Result<FakeDevice> {
    // Use real device for device tests.
    let device = FakeDevice::new(&HashMap::new());
    let mut profiler = Profiler::default();
    // Capture adb command?
    let restart_chooser = RestartChooser::new(&RestartChoice::Reboot);
    let initial_adb_cmds = HashMap::from([(
        PathBuf::from("ignore_me"),
        AdbCommand::from_action(AdbAction::Mkdir, &PathBuf::from("ignore_me")),
    )]);
    adevice::device::update(&restart_chooser, &initial_adb_cmds, &mut profiler, &device, wait)?;
    Ok(device)
}
#[test]
fn update_has_timeout_commands() -> Result<()> {
    // Wait has two parts
    // 1) The prep that clear sys.boot_completed.
    let device = call_update_with_reboot(Wait::Yes)?;
    assert!(
        device.raw_cmds().iter().any(|c| c.contains("setprop sys.boot_completed")),
        "Did not find setprop cmd, did find\n{:?}",
        device.raw_cmds()
    );
    // 2) A call to device.wait() that calls "adb timeout ..."
    // but the FakeDevice mocks this out.
    assert_eq!(1, device.wait_calls());

    Ok(())
}

#[test]
fn update_nowait_has_no_timeout_commands() -> Result<()> {
    let device = call_update_with_reboot(Wait::No)?;
    // Wait has two parts
    // 1) The prep that clear sys.boot_completed.
    assert_eq!(
        0,
        device.raw_cmds().iter().filter(|c| c.contains("setprop sys.boot_completed")).count(),
        "Found timeout cmd, did not expect to\nn{:?}",
        device.raw_cmds()
    );
    // 2) A call to device.wait() that calls "adb timeout ..."
    // but the FakeDevice mocks this out.
    assert_eq!(0, device.wait_calls());

    Ok(())
}
