# libnativehelper

libnativehelper is a collection of JNI related utilities used in Android.

There are several header and binary libraries here and not all of the
functionality fits together well. The header libraries are mostly C++
based. The binary libraries are entirely written in C with no C++
dependencies. This is by design as the code here can be distributed in
multiple ways, including mainline modules, so keeping the size down
benefits everyone with smaller downloads and a stable ABI.

## Header Libraries

### jni_headers

This is a header library that provides the API in the JNI Specification 1.6.
Any project in Android that includes `jni.h` should depend on this.

See:

* [jni.h](include_jni/jni.h)

### libnativehelper_header_only

Header library that provide utilities defined entirely within the headers. There
are scoped resource classes that make common JNI patterns of acquiring and
releasing resources safer to use than the JNI specification equivalents.
Examples being `ScopedLocalRef` to manage the lifetime of local references and
`ScopedUtfChars` to manage the lifetime of Java strings in native code and
provide access to utf8 characters.

See:

* [nativehelper/nativehelper_utils.h](header_only_include/nativehelper/nativehelper_utils.h)
* [nativehelper/scoped_utf_chars.h](header_only_include/nativehelper/scoped_utf_chars.h)
* [nativehelper/scoped_string_chars.h](header_only_include/nativehelper/scoped_string_chars.h)
* [nativehelper/scoped_primitive_array.h](header_only_include/nativehelper/scoped_primitive_array.h)
* [nativehelper/scoped_local_ref.h](header_only_include/nativehelper/scoped_local_ref.h)
* [nativehelper/scoped_local_frame.h](header_only_include/nativehelper/scoped_local_frame.h)
* [nativehelper/utils.h](header_only_include/nativehelper/utils.h)

### jni_platform_headers

The `jni_macros.h` header provide compile time checking of JNI methods
implemented in C++. They ensure the C++ method declaration match the
Java signature they are associated with.

See:

* [nativehelper/jni_macros.h](include_platform_header_only/nativehelper/jni_macros.h)

## Libraries

### libnativehelper

A shared library distributed in the ART module. It provides the JNI invocation
API from the JNI Specification, which is part of the public NDK. It also
contains the glue that connects the API implementation to the ART runtime, which
is platform only and is used with the Zygote and the standalone dalvikvm.

See:

* [nativehelper/JniInvocation.h](include_platform/nativehelper/JniInvocation.h)
* [nativehelper/JNIPlatformHelp.h](include_platform/nativehelper/JNIPlatformHelp.h)
* libnativehelper_compat_libc++ headers below

### libnativehelper_compat_libc++

This shared and static library contains a subset of the helper routines in
libnativehelper based only on public stable JNI APIs. It gets distributed with
the caller code and is preferrably linked statically since it is very thin (less
than 20 KB). The name of this library is a misnomer since it contains no C++
code.

See:

* [nativehelper/JNIHelp.h](include/nativehelper/JNIHelp.h)
* [nativehelper/ScopedUtfChars.h](include/nativehelper/ScopedUtfChars.h)
* [nativehelper/ScopedLocalFrame.h](include/nativehelper/ScopedLocalFrame.h)
* [nativehelper/ScopedLocalRef.h](include/nativehelper/ScopedLocalRef.h)
* [nativehelper/ScopedPrimitiveArray.h](include/nativehelper/ScopedPrimitiveArray.h)
* [nativehelper/ScopedStringChars.h](include/nativehelper/ScopedStringChars.h)
* [nativehelper/toStringArray.h](include/nativehelper/toStringArray.h)
* [nativehelper/Utils.h](include/nativehelper/Utils.h)
