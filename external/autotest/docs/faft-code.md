# FAFT Code Overview

_Self-link: [go/faft-code](https://goto.google.com/faft-code)_

_Last updated: 2020/03/13_

## Introduction
The FAFT framework provides a set of python modules that simplifies writing of firmware tests.  The [design doc](faft-design-doc.md) was written in 2011 and the code itself has since evolved to support hundreds of firmware tests.  This document provides a high level overview of the current code base.

## Code Architecture
The framework itself is split up into a [server side](/server/cros/faft) and a [client side](/client/cros/faft).  The server side runs on the host machine, while the client side runs on the DUT.

- ### [Client](#client-code-details)
  The client consists of an [XML rpc server](/client/cros/faft/rpc_server.py) that listens for requests from the server side, and a set of [utility classes](/client/cros/faft/utils) that are able to execute shell commands on the DUT to fulfill the requests.

- ### [Server](#server-code-details)
  The server consists of base skeleton autotest test classes that perform DUT initialization and contain references to a set of utility methods and classes that can perform useful actions.  Some actions require interaction with the DUT that is not possible via the client RPC server, so these ultimately use servod to carry out the interaction.  Others send XML RPC calls to the client on the DUT to perform the needed work.

- ### [Tests](#test-code-details)
  These are autotest style tests consisting of control file(s) and a test class.  The control file documents the test and invokes a job to run the test class.  The test class will subclass one of the base test classes, thereby gaining functionality exposed by the framework.

## Client Code Details
- [rpc_server](/client/cros/faft/rpc_server.py): Listens for RPC requests from the server and routes them to rpc_functions module.
- [rpc_functions](/client/cros/faft/rpc_functions.py): Delegates requested actions to servicers in utils.
- Servicers in [utils](/client/cros/faft/utils)
    - [cgpt_handler](/client/cros/faft/utils/cgpt_handler.py):  GUID Partition Table manipulation.
    - [firmware_check_keys](/client/cros/faft/utils/firmware_check_keys.py): Check firmware keys.
    - [firmware_updater](/client/cros/faft/utils/firmware_updater.py): Manipulate firmware images.
    - [flashrom_handler](/client/cros/faft/utils/flashrom_handler.py): Read/write/parse flashrom chip images.
    - [kernel_handler](/client/cros/faft/utils/kernel_handler.py): Manipulate chromeos kernel.
    - [os_interface](/client/cros/faft/utils/os_interface.py): Wraps misc useful shell commands such as crossystem.
    - [rootfs_handler](/client/cros/faft/utils/rootfs_handler.py): Verifies integrity of root fs.
    - [saft_flashrom_util](/client/cros/faft/utils/saft_flashrom_util.py): Interact with flashrom, used by flashrom_handler.
    - [shell_wrapper](/client/cros/faft/utils/shell_wrapper.py): Runs shell commands, used by others servicers.
    - [tpm_handler](/client/cros/faft/utils/tpm_handler.py): Controls TPM device’s NVRAM.

## Server Code Details

- ### [Base test classes](/server/cros/faft)
    - [FirmwareTest](/server/cros/faft/firmware_test.py): The main base test class.  It contains a test skeleton, methods, and references to utility classes to perform useful tasks.
    - [Cr50Test](/server/cros/faft/cr50_test.py): Base test class for firmware_Cr50 tests.  It has initialization and utility methods tailored to Cr50 tests.
    - [FingerprintTest](/server/cros/faft/fingerprint_test.py): Base test class firmware_Fingerprint tests.  It has initialization and utility methods tailored to fingerprint tests.
- ### [Utility modules](/server/cros/faft/utils)
    - [config](/server/cros/faft/utils/config.py): Table of board specific properties (makes use of [config json](/server/cros/faft/configs) files)
    - [faft_checkers](/server/cros/faft/utils/faft_checkers.py): Various assertions about DUT state.
    - [mode_switcher](/server/cros/faft/utils/mode_switcher.py): Boots the DUT into a specified mode (as in normal, developer, recovery).

## Test Code Details
The tests are those prefixed with “[firmware_](https://source.chromium.org/search?q=f:site_tests%2Ffirmware_&sq=&ss=chromiumos)” in [site_tests](/server/site_tests).  See [firmware_FAFTSetup](/server/site_tests/firmware_FAFTSetup) for a simple example.

- ### Test Directory
  This is where the control files and test classes (along with any supporting files) reside under site_tests.  It must have the same name as the test class.

- ### Control File
  The control file specifies metadata and invocation details about the test.  If multiple control files use the same test class, the naming convention is control.<variation>.

- ### Test Class
  The test class contains logic to execute the test.  It must be in a module with the same name as the test directory, and the class name must also be the same as the test directory.  Its run_once method is allowed to take additional parameters, and these will be populated according to the job invocation in the control files.  This facilitates code reuse by allowing multiple control files to share a common test class.

## Note to Contributors
If you’re fixing a bug or adding a new feature to the framework in order to support an immediate need, please write unit and/or integration tests for the code you’ve touched.  Adding some additional documentation here or in the code itself may also be helpful.
