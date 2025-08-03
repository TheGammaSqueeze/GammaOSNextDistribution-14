//! This module initializes the built-in services included in every
//! GATT server.

pub mod gap;
pub mod gatt;

use anyhow::Result;

use self::{gap::register_gap_service, gatt::register_gatt_service};

use super::gatt_database::GattDatabase;

/// Register all built-in services with the provided database
pub fn register_builtin_services(database: &mut GattDatabase) -> Result<()> {
    register_gap_service(database)?;
    register_gatt_service(database)?;
    Ok(())
}
