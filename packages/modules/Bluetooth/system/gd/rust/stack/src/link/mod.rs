//! link management

/// Exposes ACL functionality
pub mod acl;

use gddi::module;

module! {
    link_module,
    submodules {
        acl::acl_module,
    },
}
