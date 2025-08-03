mod common;
use adevice::adevice::Profiler;
use adevice::fingerprint::{self, FileMetadata};
use adevice::{cli, commands};
use anyhow::{Context, Result};
use clap::Parser;
use common::fakes::{FakeDevice, FakeHost, FakeMetricSender};
use std::collections::HashMap;
use std::path::PathBuf;

const NO_LOG_FILE: Option<std::fs::File> = None;

// Just placeholder for now to show we can call adevice.
#[test]
fn adevice_status() -> Result<()> {
    std::env::remove_var("HOME"); // Use default config, don't write one.
    let device_fs = HashMap::from([
        (PathBuf::from("system/fakefs_default_file"), file_metadata("digest1")),
        (PathBuf::from("system"), dir_metadata()),
    ]);

    let host_fs = HashMap::from([
        (PathBuf::from("system/fakefs_default_file"), file_metadata("digest1")),
        // NOTE: extra file on host
        (PathBuf::from("system/fakefs_new_file_file"), file_metadata("digest1")),
        (PathBuf::from("system"), dir_metadata()),
    ]);
    let host_tracked_files =
        vec!["system/fakefs_default_file".to_string(), "system/fakefs_new_file".to_string()];

    let fake_host = FakeHost::new(&host_fs, &host_tracked_files);
    let fake_device = FakeDevice::new(&device_fs);
    let mut stdout = Vec::new();
    // TODO(rbraunstein): Fix argv[0]
    let cli = cli::Cli::parse_from(["", "--product_out", "unused", "status"]);

    adevice::adevice::adevice(
        &fake_host,
        &fake_device,
        &cli,
        &mut stdout,
        &mut FakeMetricSender::new(),
        NO_LOG_FILE,
        &mut Profiler::default(),
    )?;
    let stdout_str = String::from_utf8(stdout).unwrap();

    // TODO(rbraunstein): Check the status group it is in: (Ready to push)
    assert!(
        stdout_str.contains(&"system/fakefs_new_file".to_string()),
        "\n\nACTUAL:\n {}",
        stdout_str
    );
    Ok(())
}

#[test]
fn lost_and_found_should_not_be_cleaned() -> Result<()> {
    std::env::remove_var("HOME"); // Use default config, don't write one.
    let device_files = HashMap::from([
        (PathBuf::from("system_ext/lost+found"), dir_metadata()),
        (PathBuf::from("system/some_file"), file_metadata("m1")),
        (PathBuf::from("system/lost+found"), dir_metadata()),
    ]);

    // Ensure the partitions exist.
    let ninja_deps = commands::split_string("system/file1 system_ext/file2");
    let fake_host = FakeHost::new(&HashMap::new(), &ninja_deps);
    let fake_device = FakeDevice::new(&device_files);
    // TODO(rbraunstein): Fix argv[0]
    let cli = cli::Cli::parse_from([
        "",
        "--product_out",
        "unused",
        "clean",
        "--force",
        "--restart",
        "none",
    ]);

    // Expect some_file, but not lost+found.
    {
        let mut stdout = Vec::new();
        let mut metrics = FakeMetricSender::new();
        adevice::adevice::adevice(
            &fake_host,
            &fake_device,
            &cli,
            &mut stdout,
            &mut metrics,
            NO_LOG_FILE,
            &mut Profiler::default(),
        )
        .context("Running adevice clean")?;
        let stdout_str = String::from_utf8(stdout).unwrap();
        assert!(stdout_str.contains("system/some_file"), "\n\nACTUAL:\n {}", stdout_str);
        assert!(!stdout_str.contains("lost+found"), "\n\nACTUAL:\n {}", stdout_str);

        assert!(fake_device.removes().contains(&PathBuf::from("system/some_file")));
        assert!(!fake_device.removes().contains(&PathBuf::from("system/lost+found")));
    }

    Ok(())
}

#[test]
fn update_should_clean_stale_files() -> Result<()> {
    std::env::remove_var("HOME");
    let device_files = HashMap::from([(PathBuf::from("system/STALE_FILE"), file_metadata("m1"))]);

    // Ensure the partitions exist.
    let ninja_deps = commands::split_string("system/other_file");
    let fake_host = FakeHost::new(&HashMap::new(), &ninja_deps);
    let fake_device = FakeDevice::new(&device_files);
    let cli = cli::Cli::parse_from(["", "--product_out", "unused", "update", "--restart", "none"]);

    // Expect some_file
    {
        let mut stdout = Vec::new();
        let mut metrics = FakeMetricSender::new();
        adevice::adevice::adevice(
            &fake_host,
            &fake_device,
            &cli,
            &mut stdout,
            &mut metrics,
            NO_LOG_FILE,
            &mut Profiler::default(),
        )
        .context("Running adevice clean")?;

        assert!(fake_device.removes().contains(&PathBuf::from("system/STALE_FILE")));
    }

    Ok(())
}

#[test]
fn update_big_fs_change() -> Result<()> {
    std::env::remove_var("HOME"); // Use default config, don't write one.
    let device_files = HashMap::from([
        // <-- STALE_FILE not on host
        (PathBuf::from("system/STALE_FILE"), file_metadata("m1")),
        (PathBuf::from("system/bin"), dir_metadata()),
        (PathBuf::from("system/bin/f1"), file_metadata("m1")),
        (PathBuf::from("system/bin/f2"), file_metadata("m1")),
        (PathBuf::from("system/bin/dir1"), dir_metadata()),
        // <-- STALE_DIR not on host
        (PathBuf::from("system/bin/dir1/STALE_DIR"), dir_metadata()),
        (PathBuf::from("system/bin/dir1/STALE_DIR/stalefile1"), file_metadata("m1")),
        (PathBuf::from("system/bin/dir1/STALE_DIR/stalefile2"), file_metadata("m1")),
        (PathBuf::from("system/bin/dir1/f1"), file_metadata("m1")),
    ]);

    let host_files = HashMap::from([
        (PathBuf::from("system/bin"), dir_metadata()),
        (PathBuf::from("system/bin/f1"), file_metadata("m1")),
        (PathBuf::from("system/bin/f2"), file_metadata("m1")),
        (PathBuf::from("system/bin/dir1"), dir_metadata()),
        (PathBuf::from("system/bin/dir1/f1"), file_metadata("m1")),
    ]);

    // Ensure the partitions exist.
    let ninja_deps = commands::split_string("system/bin/f1 system/bin/f2 system/bin/dir1/f1");
    let fake_host = FakeHost::new(&host_files, &ninja_deps);
    let fake_device = FakeDevice::new(&device_files);
    let cli = cli::Cli::parse_from(["", "--product_out", "unused", "update", "--restart", "none"]);

    // Expect some_file
    {
        let mut stdout = Vec::new();
        let mut metrics = FakeMetricSender::new();
        adevice::adevice::adevice(
            &fake_host,
            &fake_device,
            &cli,
            &mut stdout,
            &mut metrics,
            NO_LOG_FILE,
            &mut Profiler::default(),
        )
        .context("Running adevice update")?;

        assert_eq!(
            vec![
                // expected to be ordered dfs
                PathBuf::from("system/bin/dir1/STALE_DIR/stalefile2"),
                PathBuf::from("system/bin/dir1/STALE_DIR/stalefile1"),
                PathBuf::from("system/STALE_FILE"),
                PathBuf::from("system/bin/dir1/STALE_DIR"),
            ],
            fake_device.removes(),
        );
    }

    Ok(())
}

pub fn file_metadata(digest: &str) -> FileMetadata {
    FileMetadata {
        file_type: fingerprint::FileType::File,
        digest: digest.to_string(),
        ..Default::default()
    }
}

pub fn dir_metadata() -> FileMetadata {
    FileMetadata { file_type: fingerprint::FileType::Directory, ..Default::default() }
}
