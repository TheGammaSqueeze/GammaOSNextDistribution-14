//! netsim Rust grpc test client

use std::env;
use std::sync::Arc;

use grpcio::{ChannelBuilder, EnvBuilder};
use netsim_common::util::os_utils::get_server_address;
use netsim_proto::frontend_grpc::FrontendServiceClient;

fn main() {
    let args: Vec<String> = env::args().collect();
    let server_addr: String = if args.len() > 1 {
        args[1].to_owned()
    } else {
        match get_server_address(1) {
            Some(addr) => addr,
            None => {
                println!("Unable to get server address.");
                return;
            }
        }
    };
    let env = Arc::new(EnvBuilder::new().build());

    let ch = ChannelBuilder::new(env).connect(&server_addr);
    let client = FrontendServiceClient::new(ch);

    let reply = client.get_version(&::protobuf::well_known_types::empty::Empty::new()).unwrap();
    println!("Version: {}", reply.version);
}
