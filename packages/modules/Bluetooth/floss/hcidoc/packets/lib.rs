#![allow(clippy::all)]
#![allow(unused)]
#![allow(missing_docs)]

pub mod l2cap {
    include!(concat!(env!("OUT_DIR"), "/l2cap_packets.rs"));
}
