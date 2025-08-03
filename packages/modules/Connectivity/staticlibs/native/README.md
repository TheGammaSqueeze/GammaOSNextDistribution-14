# JNI
As a general rule, jarjar every static library dependency used in a mainline module into the
modules's namespace (especially if it is also used by other modules)

Fully-qualified name of java class needs to be hard-coded into the JNI .so, because JNI_OnLoad
does not take any parameters. This means that there needs to be a different .so target for each
post-jarjared package, so for each module.

This is the guideline to provide JNI library shared with modules:

* provide a common java library in frameworks/libs/net with the Java class (e.g. BpfMap.java).

* provide a common native library in frameworks/libs/net with the JNI and provide the native
  register function with class_name parameter. See register_com_android_net_module_util_BpfMap
  function in frameworks/libs/net/common/native/bpfmapjni/com_android_net_module_util_BpfMap.cpp
  as an example.

When you want to use JNI library from frameworks/lib/net:

* Each module includes the java library (e.g. net-utils-device-common-bpf) and applies its jarjar
  rules after build.

* Each module creates a native library in their directory, which statically links against the
  common native library (e.g. libnet_utils_device_common_bpf), and calls the native registered
  function by hardcoding the post-jarjar class_name. Linkage *MUST* be static because common
  functions in the file (e.g., `register_com_android_net_module_util_BpfMap`) will appear in the
  library (`.so`) file, and different versions of the library loaded in the same process by
  different modules will in general have different versions. It's important that each of these
  libraries loads the common function from its own library. Static linkage should guarantee this
  because static linkage resolves symbols at build time, not runtime.