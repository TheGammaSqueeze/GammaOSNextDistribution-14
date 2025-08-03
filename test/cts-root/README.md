# Android Compatibility Test Suite - Root extension (CTS-Root)

This is an extension the main CTS test suite, for general compatibility tests
that require root privileges or a debuggable device.

Note that the name of this suite in the build system is CTS_ROOT (with an
underscore), so this is built with `m cts_root` and tests must be tagged with
`cts_root` in their test_suites to be included.

However in tradefed the name is cts-root (with a hyphen), so use
`cts-root-tradefed` to run it, and `run cts-root` to run the default cts-root
plan.
