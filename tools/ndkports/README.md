# ndkports

A collection of Android build scripts for various third-party libraries and the
tooling to build them.

If you're an Android app developer looking to *consume* these libraries, this is
probably not what you want. This project builds AARs to be published to Maven.
You most likely want to use the AAR, not build it yourself.

Note: Gradle support for consuming these artifacts from an AAR is a work in
progress.

## Ports

Each third-party project is called a "port". Ports consist of a description of
where to fetch the source, apply any patches needed, build, install, and package
the library into an AAR.

A port is a subclass of the abstract Kotlin class `com.android.ndkports.Port`.
Projects define the name and version of the port, the URL to fetch source from,
a list of modules (libraries) to build, and the build steps.

See the [Port class] for documentation on the port API.

Individual port files are kept in `ports/$name/port.kts`. For example, the cURL
port is [ports/curl/port.kts](ports/curl/port.kts).

[Port class]: src/main/kotlin/com/android/ndkports/Port.kt

## Building a Port

We recommend using the supplied scripts and Dockerfile for consistent builds.

To build a release for distribution to a Maven repo, `scripts/build_release.sh`

To build a snapshot, `scripts/build_snapshot.sh`

You can also pass custom gradle targets: `scripts/build_snapshot.sh curl`

The scripts use the standard `ANDROID_NDK_ROOT` environment variable to
locate the NDK. For example, `ANDROID_NDK_ROOT=/path/to/ndk scripts/build_release.sh`