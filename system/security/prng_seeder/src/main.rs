// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! FIPS compliant random number conditioner. Reads from /dev/hw_random
//! and applies the NIST SP 800-90A CTR DRBG strategy to provide
//! pseudorandom bytes to clients which connect to a socket provided
//! by init.

mod conditioner;
mod cutils_socket;
mod drbg;

use std::{
    convert::Infallible,
    fs::remove_file,
    io::ErrorKind,
    os::unix::net::UnixListener,
    path::{Path, PathBuf},
};

use anyhow::{ensure, Context, Result};
use clap::Parser;
use log::{error, info, LevelFilter};
use nix::sys::signal;
use tokio::{io::AsyncWriteExt, net::UnixListener as TokioUnixListener};

use crate::conditioner::ConditionerBuilder;

#[derive(Debug, Parser)]
struct Cli {
    #[clap(long, default_value = "/dev/hw_random")]
    source: PathBuf,
    #[clap(long)]
    socket: Option<PathBuf>,
}

fn configure_logging() -> Result<()> {
    ensure!(
        logger::init(
            logger::Config::default()
                .with_tag_on_device("prng_seeder")
                .with_max_level(LevelFilter::Info)
        ),
        "log configuration failed"
    );
    Ok(())
}

fn get_socket(path: &Path) -> Result<UnixListener> {
    if let Err(e) = remove_file(path) {
        if e.kind() != ErrorKind::NotFound {
            return Err(e).context(format!("Removing old socket: {}", path.display()));
        }
    } else {
        info!("Deleted old {}", path.display());
    }
    UnixListener::bind(path)
        .with_context(|| format!("In get_socket: binding socket to {}", path.display()))
}

fn setup() -> Result<(ConditionerBuilder, UnixListener)> {
    configure_logging()?;
    let cli = Cli::try_parse()?;
    // SAFETY: Nothing else sets the signal handler, so either it was set here or it is the default.
    unsafe { signal::signal(signal::Signal::SIGPIPE, signal::SigHandler::SigIgn) }
        .context("In setup, setting SIGPIPE to SIG_IGN")?;

    let listener = match cli.socket {
        Some(path) => get_socket(path.as_path())?,
        None => cutils_socket::android_get_control_socket("prng_seeder")
            .context("In setup, calling android_get_control_socket")?,
    };
    let hwrng = std::fs::File::open(&cli.source)
        .with_context(|| format!("Unable to open hwrng {}", cli.source.display()))?;
    let cb = ConditionerBuilder::new(hwrng)?;
    Ok((cb, listener))
}

async fn listen_loop(cb: ConditionerBuilder, listener: UnixListener) -> Result<Infallible> {
    let mut conditioner = cb.build();
    listener.set_nonblocking(true).context("In listen_loop, on set_nonblocking")?;
    let listener = TokioUnixListener::from_std(listener).context("In listen_loop, on from_std")?;
    info!("Starting listen loop");
    loop {
        match listener.accept().await {
            Ok((mut stream, _)) => {
                let new_bytes = conditioner.request()?;
                tokio::spawn(async move {
                    if let Err(e) = stream.write_all(&new_bytes).await {
                        error!("Request failed: {}", e);
                    }
                });
                conditioner.reseed_if_necessary().await?;
            }
            Err(e) if e.kind() == ErrorKind::Interrupted => {}
            Err(e) => return Err(e).context("accept on socket failed"),
        }
    }
}

fn run() -> Result<Infallible> {
    let (cb, listener) = match setup() {
        Ok(t) => t,
        Err(e) => {
            // If setup fails, just hang forever. That way init doesn't respawn us.
            error!("Hanging forever because setup failed: {:?}", e);
            // Logs are sometimes mysteriously not being logged, so print too
            println!("prng_seeder: Hanging forever because setup failed: {:?}", e);
            loop {
                std::thread::park();
                error!("std::thread::park() finished unexpectedly, re-parking thread");
            }
        }
    };

    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .context("In run, building reactor")?
        .block_on(async { listen_loop(cb, listener).await })
}

fn main() {
    let e = run();
    error!("Launch terminated: {:?}", e);
    // Logs are sometimes mysteriously not being logged, so print too
    println!("prng_seeder: launch terminated: {:?}", e);
    std::process::exit(-1);
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::CommandFactory;

    #[test]
    fn verify_cli() {
        Cli::command().debug_assert();
    }
}
