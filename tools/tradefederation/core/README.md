# Trade Federation (TF / Tradefed)

TF is a test harness used to drive Android automated testing. It runs on test hosts
and monitors the connected devices, handling test scheduling & execution and device
management.

Other test harnesses like Compatibility Test Suite (CTS) and Vendor Test Suite
(VTS) use TF as a basis and extend it for their particular needs.

### Building TF:

  * source build/envsetup.sh
  * tapas tradefed-all
  * make -j8

### Getting Code Reviewed

    1. Create your change in Gerrit
    2. Add the reviewer named "Tradefed Codereview" (email: tradefed-codereview@tradefederation.google.com.iam.gserviceaccount.com)
    3. Review the code review guidance at go/tf-guidelines and go/tradefed-code-reviews
    4. GWSQ should add a couple of people from the team to review your code and give feedback.

### More information

More information at:
https://source.android.com/docs/core/tests/tradefed/

See more details about Tradefed Architecture at:
https://source.android.com/docs/core/tests/tradefed/architecture

If you are a tests writer you should start looking in the test_framework/
component which contains everything needed to write a tests in Tradefed.
