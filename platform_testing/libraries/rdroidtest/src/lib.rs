//! Test harness which supports ignoring tests at runtime.

pub mod runner;

// Re-export the attribute macros.
pub use rdroidtest_macro::{ignore_if, rdroidtest};

#[doc(hidden)]
pub use libtest_mimic as _libtest_mimic;
#[doc(hidden)]
pub use linkme as _linkme;
#[doc(hidden)]
pub use paste as _paste;

/// Macro to generate the main function for the test harness.
#[macro_export]
macro_rules! test_main {
    () => {
        #[cfg(test)]
        fn main() {
            $crate::runner::main()
        }
    };
}

/// Macro to generate a wrapper function for a single test.
///
/// # Usage
///
/// ```
/// use rdroidtest::test;
///
/// test!(test_string_equality);
/// fn test_string_equality() {
///   assert_eq!("", "");
/// }
/// ```
#[macro_export]
macro_rules! test {
    ($test_name:ident) => {
        $crate::_paste::paste!(
            #[$crate::_linkme::distributed_slice($crate::runner::RDROIDTEST_TESTS)]
            fn [< __test_ $test_name >]() -> $crate::_libtest_mimic::Trial {
                $crate::_libtest_mimic::Trial::test(
                    ::std::stringify!($test_name),
                    move || $crate::runner::run($test_name),
                )
            }
        );
    };
    ($test_name:ident, ignore_if: $ignore_expr:expr) => {
        $crate::_paste::paste!(
            #[$crate::_linkme::distributed_slice($crate::runner::RDROIDTEST_TESTS)]
            fn [< __test_ $test_name >]() -> $crate::_libtest_mimic::Trial {
                $crate::_libtest_mimic::Trial::test(
                    ::std::stringify!($test_name),
                    move || $crate::runner::run($test_name),
                ).with_ignored_flag($ignore_expr)
            }
        );
    };
}

/// Macro to generate a wrapper function for a parameterized test.
///
/// # Usage
///
/// ```
///  use rdroidtest::ptest;
///
/// /// Return (param name, param value) tuples of type `(String, T)`.
/// /// The parameter value can be any type T (not just `u32`).
/// fn my_instances() -> Vec<(String, u32)> {
///     vec![
///         ("one".to_string(), 1),
///         ("two".to_string(), 2),
///         ("three".to_string(), 3),
///     ]
/// }
///
/// ptest!(is_even, my_instances());
/// fn is_even(param: u32) {
///     // Test method takes a parameter of type T.
///     assert_eq!(param % 2, 0);
/// }
/// ```
#[macro_export]
macro_rules! ptest {
    ($test_name:ident, $param_gen:expr) => {
        $crate::_paste::paste!(
            #[$crate::_linkme::distributed_slice($crate::runner::RDROIDTEST_PTESTS)]
            fn [< __ptest_ $test_name >]() -> Vec<$crate::_libtest_mimic::Trial> {
                $param_gen.into_iter().map(|(name, val)| {
                    $crate::_libtest_mimic::Trial::test(
                        format!("{}/{}", ::std::stringify!($test_name), name),
                        move || $crate::runner::run(|| $test_name(val)),
                    )
                }).collect()
            }
        );
    };
    ($test_name:ident, $param_gen:expr, ignore_if: $ignore_expr:expr) => {
        $crate::_paste::paste!(
            #[$crate::_linkme::distributed_slice($crate::runner::RDROIDTEST_PTESTS)]
            fn [< __ptest_ $test_name >]() -> Vec<$crate::_libtest_mimic::Trial> {
                $param_gen.into_iter().map(|(name, val)| {
                    let ignored = $ignore_expr(&val);
                    $crate::_libtest_mimic::Trial::test(
                        format!("{}/{}", ::std::stringify!($test_name), name),
                        move || $crate::runner::run(|| $test_name(val)),
                    ).with_ignored_flag(ignored)
                }).collect()
            }
        );
    };
}
