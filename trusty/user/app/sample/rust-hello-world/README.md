# Hello World App in Rust

This is a rewrite of Trusty's [Hello World App](https://android.googlesource.com/trusty/app/sample/+/88c0b5406c6615e0945239700fd0b0afbc0de278/hello-world)
in Rust. It has the functionality of the original `app` and `test-app` TAs written in C, but is structured differently. It's recommended that you're familiar with the original app, as this README will reference the original.

## Download the code

- In the gerrit UI, on the top right corner, select Download Patch, then cherry-pick.

```{admonition} Tip
You can download the original TA as well, if you want to see the rust TA communicate with the original C `test-app` TA.
```

You should have a `trusty/user/app/sample/rust-hello-world` directory under your root.

## Directory Structure

The structure of this directory is significantly different. There are two different TAs here, a library(named `rust_hello_world_lib` in its manifest), and the app(named `rust_hello_world_app`). There is only one subdirectory, as the app is contained within the library directory.


```
sample
|-- rust-hello-world
|-- |-- app
|   |-- |-- main.rs
|   |-- |-- manifest.json
|   |-- |-- rules.mk
|-- |-- lib.rs
|-- |-- README.md
|-- |-- rules.mk
|-- usertests-inc.mk
`
```

* `rust-hello-world/` - This contains all the source code, and the manifest and makefile for the library TA:

    -   `lib.rs` - This file contains the functionality of the original `app`. It sets up the event loop and exposes the port
        for other trusted apps to connect onto. In addition, it contains the unit tests that can be run to emulate `test-app`.

    -   `manifest.json` - This file specifies the configuration options for the library app.

    -   `rules.mk` - This is the makefile for the library app. In order to run our unit tests in Rust, the configuration is
different.
        * We include `make/library.mk` instead of `make/trusted_app.mk`.
        * `MODULE_RUST_TESTS := true` allows us to run tests in this TA.
        * `MODULE_CRATE_NAME := hello_world_in_rust` specifies the crate name, also specifying the port to connect for tests.

* `rust-hello-world/app` - The app directory contains a separated manifest and makefile for the app TA. In addition, the source file for the app TA, `main.rs`, simply calls into `lib.rs` to start the event loop.

    -   `main.rs` - This simply contains the `main` function that runs on app startup, which calls into `lib.rs`.
    -   `manifest.json` - This manifest specifices the configuration options for the app itself.
    -   `rules.mk` - The makefile for the app.

## Reading the Source

Like the original, the best idea is to start reading the source code itself.

`main.rs` contains just a simple call to the functions in `lib.rs`, which contains all the functionality.

In `lib.rs`, there are two core traits that are implemented:
* Serialization/Deserialization - the logic for turning incoming IPC messages into a string to process, and the logic for turning a string into an IPC message to output.
* Service - The logic that handles an incoming connection, and incoming message. The `on_connect` and `on_message` methods are called whenever connections and messages are sent.

The source code has comments that explain the traits in more detail. Once you have these implementations, you can start up a Manager in `init_and_start_loop()`. The manager automatically dispatches connections and messages to our Service implementation.

In Rust, the unit tests are included in the same file as the source code. This is reflected in `lib.rs`, on the bottom of the file.

## Building and Running the Test

There are two ways to exercise the TA.
1. You can use the unit tests in `lib.rs`.
2. You can actually use the original `test-app` in C to exercise the Rust TA.

Let's start with the unit test approach.

First, you need to build the code. (You don't need to include the rust TA in `TRUSTY_BUILTIN_USER_TASKS` first)

```
$ ./trusty/vendor/google/aosp/scripts/build.py --jobs=$(nproc) --skip-tests qemu-generic-arm64-test-debug
```

Next, you run a boot test, with the crate name that was provided in the library TA's `manifest.json` ( `hello_world_in_rust` ). We prepend `com.android.trusty.rust.` and append `.test` to this crate name. Our boot test will look like this:

```
$ ./build-root/build-qemu-generic-arm64-test-debug/run     --headless --boot-test "com.android.trusty.rust.hello_world_in_rust.test" --verbose
```

It should run two tests, and pass both!

This approach uses the library TA solely. If you were curious about exercising the app TA, then you can try the second approach. You'll need to have the original CL downloaded.

Once you've done so, don't include the original C `app` in `TRUSTY_BUILTIN_USER_TASKS`. We want to include the rust app TA instead.

```
TRUSTY_BUILTIN_USER_TASKS := \
    ...
	trusty/user/app/sample/rust-hello-world/app \
```

After including this line, rebuild the QEMU target.

```
$ ./trusty/vendor/google/aosp/scripts/build.py --jobs=$(nproc) --skip-tests qemu-generic-arm64-test-debug
```

Now, you should be able to run the boot test. This will use the unit test in the original C `test-app`, which sets up a connection to our rust app TA.

```
$ ./build-root/build-qemu-generic-arm64-test-debug/run \
    --headless --boot-test "com.android.trusty.hello.test"
```

