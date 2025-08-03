// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use clap::Parser;

#[derive(Debug, Parser)]
pub struct NetsimdArgs {
    /// File descriptor start up info proto
    #[arg(short = 's', long, alias = "fd_startup_str")]
    pub fd_startup_str: Option<String>,

    /// Disable grpc server for CLI
    #[arg(long, alias = "no_cli_ui")]
    pub no_cli_ui: bool,

    /// Disable web server
    #[arg(long, alias = "no_web_ui")]
    pub no_web_ui: bool,

    /// Enable packet capture
    #[arg(long)]
    pub pcap: bool,

    /// Disable Address Reuse for test model
    #[arg(long, alias = "disable_address_reuse")]
    pub disable_address_reuse: bool,

    /// Set custom hci port
    #[arg(long, alias = "hci_port")]
    pub hci_port: Option<u32>,

    /// Enables connector mode to forward packets to another instance.
    #[arg(short, long, alias = "connector_instance", visible_alias = "connector_instance_num")]
    pub connector_instance: Option<u16>,

    /// Netsimd instance number
    #[arg(short, long, visible_alias = "instance_num")]
    pub instance: Option<u16>,

    /// Set whether log messages go to stderr instead of logfiles
    #[arg(short, long)]
    pub logtostderr: bool,

    /// Enable development mode. This will include additional features
    #[arg(short, long)]
    pub dev: bool,

    /// Set the vsock port number to be listened by the frontend grpc server
    #[arg(short, long)]
    pub vsock: Option<u16>,

    /// The name of a config file to load
    #[arg(long)]
    pub config: Option<String>,

    /// Start with test beacons
    #[arg(long, alias = "test_beacons", overrides_with("no_test_beacons"))]
    pub test_beacons: bool,

    /// Do not start with test beacons
    #[arg(long, alias = "no_test_beacons", overrides_with("test_beacons"))]
    pub no_test_beacons: bool,

    /// Disable netsimd from shutting down automatically.
    /// WARNING: This flag is for development purpose. netsimd will not shutdown without SIGKILL.
    #[arg(long, alias = "no_shutdown")]
    pub no_shutdown: bool,

    /// Print Netsimd version information
    #[arg(long)]
    pub version: bool,
}
