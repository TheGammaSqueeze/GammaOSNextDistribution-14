# Style Guide

The Bluetooth test interfaces uses [protocol buffers v3](
https://developers.google.com/protocol-buffers) as Interfaces Definition
Language.

All guidelines from the [protocol buffers style guide](
https://developers.google.com/protocol-buffers/docs/style) apply to them.

## Additional guidelines

A few additional guidelines apply to the Bluetooth test interfaces to provide
consistency and improve readability.

### Use proto3 syntax

The protobuf compiler supports proto2 and proto3 syntax, but proto3 should be
used, as it is the latest version.

### Use names from the Bluetooth specification nomenclature

This avoids adding confusion to naming, even if the names used in the Bluetooth
specification are not consistent across profiles (Gateway, Target, Controller,
Server, Client, ...).

```protobuf
service A2DP {
    rpc OpenServer(Empty) returns (Empty); // ✗ Avoid
    rpc OpenSource(Empty) returns (Empty); // ✓ OK
}
```

### Name services without prefixes or suffixes

This helps to keep short names, if you need name-spacing you should use
a package instead.

```protobuf
service A2DP {} // ✓ OK

service Host {} // ✓ OK

service A2DPServer {} // ✗ Avoid

service BluetoothHost {} // ✗ Avoid
```

### Avoid long package names

This makes the usage of the gRPC interface harder in some generated language
where long package names are uncommon, for example in rust and python.

```protobuf
package test.interfaces.bluetooth.bredr.l2cap; // ✗ Avoid
package l2cap; // ✓ OK
```

### Use standards protocol buffers types

Protocol buffers includes a lot of [well-known types](
https://developers.google.com/protocol-buffers/docs/reference/google.protobuf),
so use them instead of redefining your owns.

```protobuf
rpc L2CAP {
    Send(MyData) returns (MyEmpty); // ✗ Avoid
    Send(google.protobuf.BytesValue) returns (google.protobuf.Empty); // ✓ OK
}
```

### Describe expected errors with `oneof` fields

This allows using the protocol buffers type system to describe the possible
outcomes of the request. You don't need to describe all errors, you should only
specify the ones that are needed by the tests.

Use the [gRPC standard error model](
https://www.grpc.io/docs/guides/error/#standard-error-model) to send the other
non specified errors (like implementation specific errors).

```protobuf
message ConnectResponse {
    oneof result {
        Connection connection = 1;
        DeviceNotFoundError device_not_found = 2;
        ...
    }
}
```

### Avoid gRPC streaming if possible

There is only a few legitimate usages for gRPC streaming (such as audio
streaming) and you should avoid it otherwise.

### Use typed tokens to represent a resource

This allows the implementation to wrap their internal format for representing
the resource inside an opaque message instead of converting them.

```protobuf
// ✗ Avoid
service Host {
    rpc Connect(BdAddr) returns (Handle);
}

message Handle {
    int16 handle = 1;
}
```

```protobuf
// ✓ OK
service Host {
    rpc Connect(BdAddr) returns (Connection);
}

message Connection {
    // Internal (opaque) representation
    // of the connection by the server
    bytes cookie = 1;
}
```
