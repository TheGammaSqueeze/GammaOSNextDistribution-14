/// Assertion check for X is is within Y of Z
#[macro_export]
macro_rules! assert_near {
    ($thing:expr, $expected:expr, $error:expr) => {
        match (&$thing, &$expected, &$error) {
            (thing_val, expected_val, error_val) => {
                if thing_val < &(expected_val - error_val)
                    || thing_val > &(expected_val + error_val)
                {
                    panic!(
                        "assertion failed: {:?} is not within {:?} of {:?}",
                        &*thing_val, &*error_val, &*expected_val
                    )
                }
            }
        }
    };
}
