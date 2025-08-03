//! Do not put multiple tests in this file. Tests in the same file run in the
//! same executable, so if there are several tests in one file, only one test
//! will successfully be able to initialize the logger.

#[test]
fn multiple_init() {
    let first_init = logger::init(Default::default());
    let second_init = logger::init(Default::default());

    assert!(first_init);
    assert!(!second_init);
}
