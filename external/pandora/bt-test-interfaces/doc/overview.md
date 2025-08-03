# Pandora APIs

Pandora Bluetooth test interfaces provide a common abstraction for Bluetooth
testing tools to interact with all Bluetooth implementations, exposing all
standard Bluetooth capabilities over [gRPC](https://grpc.io/).

While all Bluetooth stacks are different in their supported profiles, features,
and corresponding APIs, the goal of Pandora is to provide a set of unified
test interfaces which they could all implement, so testing tools can be reused
and scaled across the entire Bluetooth ecosystem.

## Requirements

Since each Bluetooth stack exposes different APIs, the test interfaces must be
generic enough and must not rely on any implementation specific behavior.
However, they must ensure that they provide all the necessary access to the
existing testing tools. For this reason, the test interfaces are co-designed by
multiple teams at Google.

The test interfaces must be implemented using [gRPC](https://grpc.io/) services
and must use [protocol buffers](https://developers.google.com/protocol-buffers)
as Interface Definition Language (IDL). A Bluetooth stack under test exposing
such interfaces must thus implement a gRPC server.

The test interfaces definition must follow the [Pandora style guide](
style-guide.md).

## Abstraction level

**A test interface is defined for each Bluetooth profile (standard or custom)**.
This allows the Bluetooth stack under test to implement only the test interfaces
corresponding to its supported profiles. Additional platform/device-specific
interfaces may also be added if necessary (but should be avoided as much as
possible).

**The same test interface can be implemented at different levels of a same
stack**: for example, in Android, the Pandora Bluetooth test interfaces can be
implemented both on top of Topshim (which is a Rust shim layer just on top of
the native stack), which is advantageous as tests running at that level can
apply to ChromeOS as well as Android, or on top of the Android Bluetooth SDK
(Java) which is advantageous for Android, since the Bluetooth module includes
both the native stack and the SDK.

![Pandora APIs levels](images/pandora-apis-levels.svg)

## Optional features

As Bluetooth profiles contain optional features, some methods of the test
interfaces might not be implementable by a specific Bluetooth stack.

Such unimplemented methods must return an [UNIMPLEMENTED](
https://grpc.github.io/grpc/core/md_doc_statuscodes.html) status code as defined
by gRPC.

Discovering which features are supported by a Bluetooth stack is not (yet) part
of the test interfaces as this is already doable via Bluetooth SIG
[Implementation Conformance Statements (ICS)](
https://www.bluetooth.com/specifications/qualification-test-requirements/).
