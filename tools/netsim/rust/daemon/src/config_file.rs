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

/// Configuration file for netsim
use netsim_proto::config::Config;
use protobuf_json_mapping::merge_from_str;
use std::fs;
use std::net::{Ipv4Addr, Ipv6Addr};

#[allow(dead_code)]
pub fn new_from_file(filename: &str) -> Result<Config, String> {
    let contents = fs::read_to_string(filename)
        .map_err(|e| format!("Failed to read config file {}: {} ", filename, e))?;
    from_str(&contents)
}

pub fn from_str(contents: &str) -> Result<Config, String> {
    let mut config = Config::new();
    merge_from_str(&mut config, contents).map_err(|e| format!("Failed to parse config: {}", e))?;
    validate_wifi(&config.wifi)?;
    Ok(config)
}

fn validate_wifi(wifi: &netsim_proto::config::WiFi) -> Result<(), String> {
    validate_ipv4(&wifi.slirp_options.vnet, "vnet")?;
    validate_ipv4(&wifi.slirp_options.vhost, "vhost")?;
    validate_ipv4(&wifi.slirp_options.vmask, "vmask")?;
    validate_ipv6(&wifi.slirp_options.vprefix6, "vprefix6")?;
    validate_ipv6(&wifi.slirp_options.vhost6, "vhost6")?;
    Ok(())
}

fn validate_ipv4(in_addr: &str, field: &str) -> Result<(), String> {
    if !in_addr.is_empty() {
        in_addr.parse::<Ipv4Addr>().map_err(|e| format!("Invalid {}: {}", field, e))?;
    }
    Ok(())
}

fn validate_ipv6(in6_addr: &str, field: &str) -> Result<(), String> {
    if !in6_addr.is_empty() {
        in6_addr.parse::<Ipv6Addr>().map_err(|e| format!("Invalid {}: {}", field, e))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_json() {
        let config = from_str(
            r#"
        {
          "wifi" : {
             "slirp_options" : {
                "disabled" : true,
                "vnet" : "192.168.1.1"
             }
          }
        }"#,
        );
        eprintln!("{:?}", config);
        assert!(config.as_ref().ok().is_some());
        let config = config.unwrap();
        assert!(config.wifi.slirp_options.as_ref().unwrap().disabled);
    }
}
