Floss
======

TODO: Fill in some more information about the Floss service

# DBus API

## Methods

The Floss DBus API is created by Rust macro and the exact definition is spread
out across multiple source files. In general, you can find relevant information
in a few places:

- Destination: this is always `org.chromium.bluetooth`
- Interface name: these are defined in each service's source file. For example,
  `BatteryProviderManager` has a DBus definition in
  `src/iface_battery_provider_manager.rs` and it's interface name is defined
  above `impl IBatteryProviderManager for IBatteryProviderManagerDBus`
  (`org.chromium.bluetooth.BatteryProviderManager`).
- Method name: these are also defined in the service source file, above the Rust
  method implementation. Following the above example, just before the
  `register_battery_provider` implementation is the declaration of its
  corresponding DBus method (`RegisterBatteryProvider`)
- Object path: these are defined in `src/interface_manager.rs`. For most
  services this is going to be `/org/chromium/bluetooth/hci{index}/{service}`
  where `{index}` depends on which adapter is being used and `{service}` is
  defined in `interface_manager.rs`.

## Objects

TODO: Explain the typical object structure

## Full API

TODO: List out all of the API
