//! Update an Android device with locally built changes.
mod adevice;
mod cli;
mod commands;
mod device;
mod fingerprint;
mod metrics;
mod progress;
mod restart_chooser;
mod tracking;
use tracing::{error, info};

use crate::adevice::Profiler;
use crate::adevice::RealHost;
use crate::device::RealDevice;
use crate::metrics::MetricSender;
use crate::metrics::Metrics;

use clap::Parser;
use std::fs::File;
use std::path::PathBuf;

use anyhow::Result;

fn main() -> Result<()> {
    let total_time = std::time::Instant::now();
    let host = RealHost::new();
    let cli = cli::Cli::parse();
    let mut profiler = Profiler::default();
    let device = RealDevice::new(cli.global_options.serial.clone());
    let mut metrics = Metrics::default();
    let result = crate::adevice::adevice(
        &host,
        &device,
        &cli,
        &mut std::io::stdout(),
        &mut metrics,
        log_file(),
        &mut profiler,
    );

    // cleanup tasks (metrics, profiling)
    match result {
        Ok(()) => metrics.add_exit_event("", 0),
        Err(ref err) => {
            metrics.add_exit_event(&err.to_string(), 1);
            error!("{}", err.to_string());
        }
    }
    progress::stop();
    profiler.total = total_time.elapsed();
    metrics.add_profiler_events(&profiler);
    println!(
        "\nFinished in {} secs, [Logfile at $ANDROID_BUILD_TOP/out/adevice.log]",
        profiler.total.as_secs()
    );
    info!("TIMING: {}", profiler.to_string());

    result
}

/// Return a file open at $ANDROID_BUILD_TOP/out/adevice.log or None
/// Ideally, use the file_rotate crate: https://docs.rs/file-rotate/latest/file_rotate/ as well.
fn log_file() -> Option<File> {
    match std::env::var("ANDROID_BUILD_TOP") {
        Ok(top) if !top.is_empty() => {
            let path = PathBuf::from(top).join("out").join("adevice.log");
            File::create(path).ok()
        }
        _ => None,
    }
}
