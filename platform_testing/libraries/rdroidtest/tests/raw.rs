//! Test use of `rdroidtest`.

use rdroidtest::{ptest, test};

// Tests using raw declarative macros.

test!(one_plus_one);
fn one_plus_one() {
    let result = 1 + 1;
    assert_eq!(result, 2);
}

test!(grumble, ignore_if: feeling_happy());
fn grumble() {
    let result = 1 + 1;
    assert_eq!(result, 2);
}

test!(clap_hands, ignore_if: !feeling_happy());
fn clap_hands() {
    let result = 1 + 1;
    assert_eq!(result, 3);
}

fn feeling_happy() -> bool {
    false
}

ptest!(is_less_than_five, my_instances());
fn is_less_than_five(param: u32) {
    assert!(param < 5);
}

ptest!(is_even, my_instances(), ignore_if: feeling_odd);
fn is_even(param: u32) {
    assert_eq!(param % 2, 0);
}

ptest!(is_odd, my_instances(), ignore_if: |p| !feeling_odd(p));
fn is_odd(param: u32) {
    assert_eq!(param % 2, 1);
}

fn feeling_odd(param: &u32) -> bool {
    *param % 2 == 1
}

fn my_instances() -> Vec<(String, u32)> {
    vec![("one".to_string(), 1), ("two".to_string(), 2), ("three".to_string(), 3)]
}

ptest!(is_odder, wrapped_instances(), ignore_if: |p| !feeling_odder(p));
fn is_odder(param: Param) {
    assert_eq!(param.0 % 2, 1);
}

fn feeling_odder(param: &Param) -> bool {
    param.0 % 2 == 1
}

struct Param(u32);

fn wrapped_instances() -> Vec<(String, Param)> {
    vec![
        ("one".to_string(), Param(1)),
        ("two".to_string(), Param(2)),
        ("three".to_string(), Param(3)),
    ]
}

ptest!(is_the_one, more_instances(), ignore_if: |p| p != "one");
fn is_the_one(param: String) {
    assert_eq!(param, "one");
}

fn more_instances() -> Vec<(String, String)> {
    vec![("one".to_string(), "one".to_string()), ("two".to_string(), "two".to_string())]
}
