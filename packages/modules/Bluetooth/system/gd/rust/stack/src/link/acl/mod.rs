//! ACL management

/// Exposes classic ACL functionality
pub mod classic;
mod core;
mod fragment;

use gddi::module;

module! {
    acl_module,
    submodules {
        classic::classic_acl_module,
        core::core_module,
    },
}
