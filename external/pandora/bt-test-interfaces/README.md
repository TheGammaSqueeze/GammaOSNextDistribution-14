# Pandora Bluetooth test interfaces

Pandora Bluetooth test interfaces provide a common abstraction for Bluetooth
testing tools to interact with all Bluetooth implementations, exposing all
standard Bluetooth capabilities over [gRPC](https://grpc.io/). They are built
using the following [requirements](doc/overview.md) and [style guide](
doc/style-guide.md).

A test interface is defined for each Bluetooth profile.

## Supported profiles

* **Host**: Interface for general methods (reset, connection, advertise...).
* **Security**: Interface to trigger Bluetooth Host security pairing procedures.
* **A2DP**: Interface for the Advanced Audio Distribution Profile.
