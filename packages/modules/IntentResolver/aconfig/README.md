# AConfig Flag libraries

Generated java flag libraries.

### FeatureFlagsLib

__Flags__
* Static singleton provider for FeatureFlags impl
* Overridable with setFeatureFlags/unsetFeatureFlags

* __FeatureFlags__
* The generated flags interface, one boolean function per flag

__FeatureFlagsImpl__
* For production code
* Real implementation using DeviceConfig

__FakeFeatureFlagsImpl__
* a configurable stateful fake (get/set/clear)
* Use with Dagger to inject across multiple components for integration tests
