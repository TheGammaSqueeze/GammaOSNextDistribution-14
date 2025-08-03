//! Test use of `rdroidtest` attribute macro.

use rdroidtest::{ignore_if, rdroidtest};

mod raw;

#[rdroidtest]
fn one_plus_one() {
    let result = 1 + 1;
    assert_eq!(result, 2);
}

#[rdroidtest]
#[ignore_if(feeling_happy())]
fn grumble() {
    let result = 1 + 1;
    assert_eq!(result, 2);
}

#[rdroidtest]
#[ignore_if(!feeling_happy())]
fn clap_hands() {
    let result = 1 + 1;
    assert_eq!(result, 3);
}

fn feeling_happy() -> bool {
    false
}

#[rdroidtest(my_instances())]
fn is_less_than_five(param: u32) {
    assert!(param < 5);
}

#[rdroidtest(my_instances())]
#[ignore_if(feeling_odd)]
fn is_even(param: u32) {
    assert_eq!(param % 2, 0);
}

#[rdroidtest(my_instances())]
#[ignore_if(|p| !feeling_odd(p))]
fn is_odd(param: u32) {
    assert_eq!(param % 2, 1);
}

fn feeling_odd(param: &u32) -> bool {
    *param % 2 == 1
}

fn my_instances() -> Vec<(String, u32)> {
    vec![("one".to_string(), 1), ("two".to_string(), 2), ("three".to_string(), 3)]
}

#[rdroidtest(wrapped_instances())]
#[ignore_if(|p| !feeling_odder(p))]
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

#[rdroidtest(more_instances())]
#[ignore_if(|p| p != "one")]
fn is_the_one(param: String) {
    assert_eq!(param, "one");
}

fn more_instances() -> Vec<(String, String)> {
    vec![("one".to_string(), "one".to_string()), ("two".to_string(), "two".to_string())]
}

#[rdroidtest]
#[ignore]
fn ignore_me() {
    panic!("shouldn't run!");
}

#[rdroidtest]
#[ignore_if(false)]
#[ignore]
fn ignore_me_too() {
    panic!("shouldn't run either -- attribute trumps ignore_if!");
}

#[rdroidtest]
#[ignore]
#[ignore_if(false)]
fn ignore_me_as_well() {
    panic!("shouldn't run either -- attribute trumps ignore_if, regardless of order!");
}

#[rdroidtest(my_instances())]
#[ignore]
fn ignore_all(param: u32) {
    panic!("parameterized test ({param}) shouldn't run");
}

rdroidtest::test_main!();
