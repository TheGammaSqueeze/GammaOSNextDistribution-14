# Gabeldorsche Style Guide

[TOC]

## Base

In general, when not mentioned in this document, developers should follow the
Google C++ and Google Java style guide as much as possible.

### Google C++ Style Guide

C++ Style Guide: https://google.github.io/styleguide/cppguide.html

### Android and Google Java Style Guide

1.  Android Java Style Guide:
    https://source.android.com/setup/contribute/code-style

2.  when not covered by (1), see External Java Style Guide:
    https://google.github.io/styleguide/javaguide.html

line length limit is 120 characters for C++ and Java

### Python Style Guide

The GD stack uses the Google Python Style Guide:

*   http://google.github.io/styleguide/pyguide.html

with the following modifications as shown in the
[.style.yapf](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/refs/heads/master/.style.yapf) definition:

```yapf
based_on_style: google
indent_width: 4
column_limit: 120
```

## Build files

*   One build target for the entire stack in packages/modules/Bluetooth/system (i.e. one cc_library())
    *   If only part of the stack needs to be compiled, configure it using the
        “target” configuration in Android.bp
*   One build target for all unit tests (i.e. one cc_test)
*   When needed, filgroup() can be created in Android.bp in sub-directories. The
    main build target should use these filegroups() to build the main output
    library.
*   All targets must have host_supported == true unless it is dependent on the
    OS
*   If the stack needs to be compiled using other build system, then the build
    files should also live in packages/modules/Bluetooth/system

## Namespace and include

*   Namespace must follow directory names
*   Top level namespace for internal code is “bluetooth”
*   Top level namespace for externally visible code is “android::bluetooth”
*   Include path must be relative to the root directory of the stack. Normally
    it is packages/modules/Bluetooth/system, for GD refactor code, it is packages/modules/Bluetooth/system/gd

## Multiple implementations of the same header

Since GD interact with many lower level components that are platform dependent,
frequently there is need to implement the same header multiple times for
different platform or hardware. When doing this:

*   Avoid #define macros as much as possible. Instead put code into different
    source files and selectively compile them for different targets.
*   Convention of operating system used:
    *   android/
        *   All Android devices that use HIDL
    *   linux/
        *   All non-Android linux devices
    *   linux_generic/
        *   Android and non-Android linux devices

## Directory structure

Root directory under Android tree:
[**packages/modules/Bluetooth/system/gd/**](https://android.googlesource.com/platform/packages/modules/Bluetooth/system/+/refs/heads/master/gd/)

*   Directory structure should be as flat as possible
*   Each file should contain at most one class
*   Header, source code, and unit test should live in the same directory with
    the following naming guideline:
    *   Source: bb.cc
    *   Header: bb.h
    *   Test: bb_test.cc
*   Each profile should have its own directory and module
*   Source and sink, server and client profiles should live in two sub folders
    of the same common directory where common code can be stored. However,
    source and sink must have separate modules
*   Module file is also the external API header
*   Prefer underscore over dashes

### Example: utility library with OS dependent implementation

*   os/: OS dependent classes such as Alarm, Thread, Handler
    *   Android.bp: Build file that defines file groups that would include
        different source files based on compile time target
    *   alarm.h: common header for alarm
    *   linux_generic/: Implementations for generic Linux OS
        *   alarm.cc: Linux generic implementation of alarm.h using timer_fd
        *   alarm_test.cc: unit test for alarm.h
    *   fuzz/: library needed for fuzz tests in the os/ library

### Example: module with hardware dependent implementation

*   hal/: Hardware abstraction layer such as HCI interfaces, Audio interfaces
    *   Android.bp: Build file that defines file groups that would include
        different source files based on compile time target
    *   hci_hal.h: common library header
    *   hci_hal_android_hidl.cc: implementation of hci_hal.h using Android HIDL
    *   hci_hal_android_hidl_test.cc: unit tests for the Android HIDL
        implementation
    *   hci_hal_host.cc: implementation of hci_hal.h using linux Bluetooth HCI
        socket
    *   hci_hal_host_rootcanal.cc: implementation of hci_hal.h using root-canal
        emulator
    *   hci_hal_host_test.cc: unit tests for the socket based HAL (root-canal)
        emulator implementation
    *   facade.proto: gRPC automation interface definition for this layer
    *   facade.h/cc: an implementation of the above gRPC interface for the GD
        stack
    *   cert/: certification tests for this module
    *   fuzz/: library needed for fuzz tests in the hal/ module

### Example: similar protocol with the same base

*   l2cap/: L2CAP layer, splitted among classic and LE
    *   classic/: Classic L2CAP module
        *   cert/: certification tests for this module
        *   internal/: internal code to be used only in classic
        *   Source code and headers being exported to other modules
    *   le/: LE L2CAP module
        *   cert/: certification tests for this module
        *   internal/: internal code to be used only in classic
        *   Source code and headers being exported to other modules
    *   internal/: L2CAP internal code that should not be used by sources
        outside L2CAP
        *   data_pipeline_manager.h
        *   data_pipeline_manager.cc
        *   data_pipeline_manager_mock.h: Mock of this class, used in unit tests
        *   dynamic_channel_allocator.h
        *   dynamic_channel_allocator.cc
        *   dynamic_channel_allocator_test.cc: GTest unit test of this class
        *   dynamic_channel_allocator_fuzz_test.cc: Fuzz test of this class
    *   *.h/.cc: Common headers and sources that is exposed to other modules

### Example: protocol or profiles with client and server side implementations

*   a2dp/: A2DP profile
    *   sink/: A2DP sink module (e.g. headset)
    *   source/: A2DP source module (e.g. phone)
*   avrcp/
    *   controller/: AVRCP peripheral module (e.g. carkit)
    *   target/: AVRCP target module (e.g. Phone)
*   hfp/
    *   hf/: Handsfree device (e.g. headset)
    *   ag/: Audio gateway (e.g. phone)

## External libraries

To maintain high portability, we are trying to stick with C++ STL as much as
possible. Hence, before including an external library, please ask the team for
review.

Examples of currently used libraries:

*   boringssl: Google's openssl implementation
*   small parts of libchrome, to be removed or replaced eventually
    *   base::OnceCallback
    *   base::Callback
    *   base::BindOnce
    *   base::Bind
*   google-breakpad: host binary crash handler
*   libunwindstack: print stacktrace on crash on host

## Exposed symbols

Given that entire Fluoride library is held in libbluetooth.so dynamic library
file, we need a way to load this library and extract entry points to it. The
only symbols that should be exposed are:

*   An entry point to a normal running adapter module libbluetooth.so
*   A header library to all exposed API service to profiles and layers
*   An entry point to a certification interface, libbluetooth\_certification.so
*   A header library to this certification stack

## Logging

Gabeldorsche uses `printf` style logging with macros defined in `os/log.h`. Five
log levels are available.

*   LOG_VERBOSE(fmt, args...): Will be disabled by default
*   LOG_INFO(fmt, args...): Will be disabled by default
*   LOG_INFO(fmt, args...): Enabled
*   LOG_WARN(fmt, args...): Enabled
*   LOG_ERROR(fmt, args...): Enabled
*   LOG_ALWAYS_FATAL(fmt, args...): Enabled, will always crash
*   ASSERT(condition): Enabled, will crash when condition is false
*   ASSERT_LOG(conditon, fmt, args...): Enabled, will crash and print log when
    condition is false

In general, errors that are caused by remote device should never crash our stack
and should be logged using LOG_WARN() only. Recoverable errors due to our stack
or badly behaved bluetooth controller firmware should be logged using
LOG_ERROR() before recovery. Non-recoverable errors should be logged as
LOG_ALWAYS_FATAL() to crash the stack and restart.
