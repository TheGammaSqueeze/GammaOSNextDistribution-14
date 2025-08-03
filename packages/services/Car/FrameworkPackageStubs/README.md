# Car FrameworkPackageStubs

Car FrameworkPackageStubs handles certain [Common intents] and informs users
they are not supported by android.hardware.type.automotive devices. This keeps
users informed, simplifies app development and avoids app crashing even when
apps do not check resolveActivity() first nor handle ActivityNotFoundException.

## How to add common intents

The steps are:

1. Ensure the intents should be no-ops according to CDD, CTS, developer guides,
   etc.
2. Add the same activity intent-filter to the AndroidManifest.xml as them in the
   original package.
3. Add a stub class in the Stubs.java to show the toast. You may also customize
   the message as needed.
4. Remove the original package handling those intents from the build targets.
5. Add & pass [CarFrameworkPackageStubsTest].
6. Validate the build targets can pass the relevant CTS and sample apps will not
   crash.

## References

1. CDD: [3.2.3.1. Common Application Intents]
2. CTS: [AvailableIntentsTest.java]
3. Developer guides: [Common Intents (API 31)]

[CarFrameworkPackageStubsTest]: ../tests/CarFrameworkPackageStubsTest/README.md

[Common intents]: https://developer.android.com/guide/components/intents-common

[3.2.3.1. Common Application Intents]: https://source.android.com/docs/compatibility/12/android-12-cdd#3231_common_application_intents

[AvailableIntentsTest.java]: ../../../../cts/tests/tests/content/src/android/content/cts/AvailableIntentsTest.java

[Common Intents (API 31)]: https://developer.android.com/about/versions/12/reference/common-intents-31
