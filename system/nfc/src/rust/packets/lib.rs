//! reimport of generated packets (to go away once rust_genrule exists)

#![allow(clippy::all)]
#![allow(unused)]
#![allow(missing_docs)]

pub mod nci {
    include!(concat!(env!("OUT_DIR"), "/nci_packets.rs"));
}
