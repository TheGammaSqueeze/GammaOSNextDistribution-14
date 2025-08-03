// Copyright 2019 TiKV Project Authors. Licensed under Apache-2.0.

#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(non_upper_case_globals)]
#[allow(clippy::all)]
mod bindings {
    #[cfg(not(soong))]
    include!(env!("BINDING_PATH"));
    // ANDROID's build system doesn't support environment variables
    // so we hardcode the output location of the bindings here.
    #[cfg(soong)]
    include!(concat!(env!("OUT_DIR"), "/grpc-bindings.rs"));
}
mod grpc_wrap;

pub use bindings::*;
pub use grpc_wrap::*;
