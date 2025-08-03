//! SQLite bindgen configuration

use bindgen::callbacks::{IntKind, ParseCallbacks};

#[derive(Debug)]
struct SqliteTypeChooser;

impl ParseCallbacks for SqliteTypeChooser {
    fn int_macro(&self, _name: &str, value: i64) -> Option<IntKind> {
        if value >= i32::min_value() as i64 && value <= i32::max_value() as i64 {
            Some(IntKind::I32)
        } else {
            None
        }
    }
}

fn main() {
    bindgen_cmd::build(|mut builder| {
        builder = builder
            .parse_callbacks(Box::new(SqliteTypeChooser))
            .rustfmt_bindings(true)
            .blocklist_function("sqlite3_vmprintf")
            .blocklist_function("sqlite3_vsnprintf")
            .blocklist_function("sqlite3_str_vappendf")
            .blocklist_type("va_list")
            .blocklist_type("__builtin_va_list")
            .blocklist_type("__gnuc_va_list")
            .blocklist_type("__va_list_tag")
            .blocklist_item("__GNUC_VA_LIST");
 
        if cfg!(feature = "unlock_notify") {
            builder = builder.clang_arg("-DSQLITE_ENABLE_UNLOCK_NOTIFY");
        }
        if cfg!(feature = "preupdate_hook") {
            builder = builder.clang_arg("-DSQLITE_ENABLE_PREUPDATE_HOOK");
        }
        if cfg!(feature = "session") {
            builder = builder.clang_arg("-DSQLITE_ENABLE_SESSION");
        }

        builder
    })
}
