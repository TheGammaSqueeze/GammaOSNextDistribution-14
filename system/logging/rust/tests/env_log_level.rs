//! Do not put multiple tests in this file. Tests in the same file run in the
//! same executable, so if there are several tests in one file, only one test
//! will successfully be able to initialize the logger.

use std::env;

#[test]
fn env_log_level() {
    env::set_var("RUST_LOG", "debug");
    assert!(logger::init(Default::default()));

    if cfg!(target_os = "android") {
        // android_logger does not read from environment variables
        assert_eq!(log::max_level(), log::LevelFilter::Off);
    } else {
        // env_logger reads its log level from the "RUST_LOG" environment variable
        assert_eq!(log::max_level(), log::LevelFilter::Debug);
    }
    env::remove_var("RUST_LOG");
}
