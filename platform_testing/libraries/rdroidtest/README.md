# rdroidtest

This is a custom Rust test harness which allows tests to be ignored at runtime based on arbitrary
criteria. The built-in Rust test harness only allows tests to be ignored at compile time, but this
is often not enough on Android, where we want to ignore tests based on system properties or other
characteristics of the device on which the test is being run, which are not known at build time.

## Usage

Unfortunately without the built-in support that rustc provides to the standard test harness, this
one is slightly more cumbersome to use. Firstly, add it to the `rust_test` build rule in your
`Android.bp` by adding the defaults provided:

```soong
rust_test {
    name: "mycrate.test",
    defaults: ["rdroidtest.defaults"],
    // ...
}
```

If you are testing a binary that has a `main` function, you'll need to remove it from the test
build:

```rust
#[cfg(not(test))]
fn main() {
    // ...
}
```

(If you're testing a library or anything else which doesn't have a `main` function, you don't need
to worry about this.)

Each test case should be marked with the `rdroidtest` attribute, rather than the standard
`#[test]` attribute:

```rust
use rdroidtest::rdroidtest;

#[rdroidtest]
fn one_plus_one() {
    assert_eq!(1 + 1, 2);
}
```

To ignore a test, you can add an `ignore_if` attribute whose argument is an expression that
evaluates to a boolean:

```rust
use rdroidtest::{ignore_if, rdroidtest};

#[rdroidtest]
#[ignore_if(!feeling_happy())]
fn clap_hands() {
    assert!(HANDS.clap().is_ok());
}
```

Somewhere in your main module, you need to use the `test_main` macro to generate an entry point for
the test harness:

```rust
rdroidtest::test_main!();
```

You can then run your tests as usual with `atest`.


## Parameterized Tests

To run the same test multiple times with different parameter values, add an argument to the
`rdroidtest` attribute:

```rust
use rdroidtest::rdroidtest;

#[rdroidtest(my_instances())]
fn is_even(param: u32) {
    assert_eq!(param % 2, 0);
}
```

The initial argument to the `rdroidtest` attribute is an expression that generates the set of
parameters to invoke the test with.  This expression should evaluate to a vector of `(String, T)`
values for some type `T`:

```rust
fn my_instances() -> Vec<(String, u32)> {
    vec![
        ("one".to_string(), 1),
        ("two".to_string(), 2),
        ("three".to_string(), 3),
    ]
}
```

The test method will be invoked with each of the parameter values in turn, passed in as the single
argument of type `T`.

Parameterized tests can also be ignored, using an `ignore_if` attribute.  For a parameterized test,
the argument is an expression that emits a boolean when invoked with a single argument, of type
`&T`:

```rust
#[rdroidtest(my_instances())]
#[ignore_if(feeling_odd)]
fn is_even_too(param: u32) {
    assert_eq!(param % 2, 0);
}

fn feeling_odd(param: &u32) -> bool {
    *param % 2 == 1
}
```

## Summary Table

|               |  Normal              | Conditionally Ignore                          |
|---------------|----------------------|-----------------------------------------------|
| Normal        | `#[rdroidtest]`      | `#[rdroidtest]` <br> `#[ignore_if(<I>)]`      |
| Parameterized | `#[rdroidtest(<G>)]` | `#[rdroidtest(<G>)]` <br> `#[ignore_if(<C>)]` |

Where:
- `<I>` is an expression that evaluates to a `bool`.
- `<G>` is an expression that evaluates to a `Vec<String, T>`.
- `<C>` is an callable expression with signature `fn(&T) -> bool`.
