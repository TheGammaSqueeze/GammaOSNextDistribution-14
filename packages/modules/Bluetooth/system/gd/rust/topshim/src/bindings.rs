#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
// TODO(b/203002625) - since rustc 1.53, bindgen causes UB warnings
// Remove this once bindgen figures out how to do this correctly
#![allow(deref_nullptr)]

include!(concat!(env!("OUT_DIR"), "/bindings.rs"));
