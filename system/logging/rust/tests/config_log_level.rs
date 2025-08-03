//! Do not put multiple tests in this file. Tests in the same file run in the
//! same executable, so if there are several tests in one file, only one test
//! will successfully be able to initialize the logger.

use std::env;

#[test]
fn config_log_max_level() {
    // Environment variables should be overwritten by config values.
    env::set_var("RUST_LOG", "debug");

    let init_result =
        logger::init(logger::Config::default().with_max_level(log::LevelFilter::Trace));

    assert!(init_result);
    // Setting the level through the Config struct should impact both host and device
    assert_eq!(log::max_level(), log::LevelFilter::Trace);

    env::remove_var("RUST_LOG");
}
