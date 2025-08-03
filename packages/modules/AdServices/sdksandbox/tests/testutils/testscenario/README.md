# SDK Sandbox Test Scenario Test Utilities

## Overview

This directory contains utilities that allow you to run tests inside of an SDK.

[design](http://go/sandbox-webview-cts-tests) (*only visible to Googlers)

Three public class are provided:
- SdkSandboxScenarioRule: This is a custom JUnit rule used for creating a test environment
and invoking test methods
- SdkSandboxTestScenarioRunner: This is a custom SandboxedSdkProvider that manages test
invocations and reporting the results from within a SDK Sandbox

## Creating new SDK Runtime tests

A simple example of using SDK Runtime testscenario utilities can be found in
//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/example.

If you need to add an entirely new JUnit test suite, you need to add both a JUnit test
suite, and a new testable SDK.

### Create a new Test SDK

You will first need to define a new Sandbox SDK with your
test cases. Create a new module for your sdk side tests.
Inside it, create three new files:
- AndroidManifest.xml
- Android.bp
- src/<package/path>/\<TestName>TestSdk.java

Write to the `Android.bp`:

```soong
android_test_helper_app {
    name: "<TestName>TestSdk",
    manifest: "AndroidManifest.xml",
    // This is a certificate provided by the
    // sandbox test utilities that will be used
    // by your JUnit test suite to load
    // this sdk.
    certificate: ":sdksandbox-test",
    srcs: [
       "src/**/*.java",
    ],
    platform_apis: true,
    // This runner is used to execute SDK side tests.
    static_libs: ["CtsSdkSandboxTestRunner"],
    libs: ["android.test.base"],
}
```

Write to the `AndroidManifest.xml`:

```xml
<!-- This is all normal configuration for a Sandbox SDK provider -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="<your.test.package>">

    <application>
        <sdk-library android:name="<your.test.package>"
                     android:versionMajor="1" />
        <property android:name="android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME"
                  android:value="<your.test.package>.<TestName>TestSdk" />
    </application>
</manifest>
```

Finally define a test within \<TestName>Sdk.java (eg: ExampleSandboxTestSdk):

```java
import com.android.sdkSandboxTestUtils.SdkSandboxTestScenarioRunner;

// The SdkSandboxTestScenarioRunner will be responsible
// for listening for commands to execute tests from
// the JUnit test suite and returning results.
public class ExampleSandboxTestSdk extends SdkSandboxTestScenarioRunner {
    @Override
    public View beforeEachTest(Context windowContext, Bundle params, int width, int height) {
        // You can optionally override this method to return a view
        // that should be added to the sandbox before each test.
        // ...
    }

    @Test
    public void testExample() {
        // You write tests as you normally would.
        // These test failures will be propagated back to the JUnit
        // test suite.
        assertTrue(true);
    }

    // You can optionally expect parameters
    // as part of the test definitions.
    // This is useful for when tests require
    // setup externally and need configuration.
    // For example, a local host server is set up
    // outside this test and this test needs to know
    // what port it's running on.
    @Test
    public void testExampleWithParam(Bundle params) {
        params.getString("someParameter");
    }
}
```

These tests will not execute on their own.
They need to be invoked from a JUnit test suite.
We will add this in the next section.

### Invoke from a JUnit test suite

This guide will skip over defining a new JUnit test suite,
as this is the same as any other JUnit test suite in CTS.

Within the `AndroidManifest.xml`, specify that the
JUnit test suite APK relies on the SDK you defined:

```xml
<application>
    <!-- Note the certificate should be what is defined here - this is sdksandbox-test -->
    <uses-sdk-library android:name="<your.test.sdk.package>"
        android:versionMajor="1"
        android:certDigest="0B:44:2D:88:FA:A7:B3:AD:23:8D:DE:29:8A:A1:9B:D5:62:03:92:0B:BF:D8:D3:EB:C8:99:33:2C:8E:E1:15:99" />
</application>
```

In `AndroidTest.xml`, specify that your test needs to install
the SDK you created:

```xml
<target_preparer class="com.android.tradefed.targetprep.suite.SuiteApkInstaller">
    <option name="test-file-name" value="<TestName>Sdk" />
</target_preparer>
```

Finally, define that your SDK should
be built and your JUnit test suite relies on
CtsSdkSandboxTestScenario in your `Android.bp`:

```soong
...
static_libs: [
    // This will be used to invoke test methods
    // from within the test Sandbox SDK.
    "CtsSdkSandboxTestScenario",
],
data: [
    // Define your test SDK as a data
    // dependency so that it is built before
    // this JUnit test suite is built.
    ":<TestName>TestSdk",
],
...
```

You can now invoke tests from your JUnit test suite as shown. Note that
SdkSandboxScenarioRule, when annotated as a @ClassRule, will create a new
activity for each test, but persist the same test SDK for all tests. If for some
reason the SDK is not found by a test, it will attempt to reload it.

If you wish to run your tests in a way such that the test SDK is reloaded after
each test, you can simply annotate the SdkSandboxScenarioRule as a @Rule and
everything else remains the same.

```java
import android.app.sdksandbox.testutils.testscenario.SdkSandboxScenarioRule;

public class ExampleSandboxTest {
    // This rule will automatically create a new test activity
    // and for each test but persist the Sdk.
    @ClassRule
    public static final SdkSandboxScenarioRule sdkTester = new SdkSandboxScenarioRule(
        "your.test.sdk.package");

    @Test
    public void testExample() throws Throwable {
        // This method will invoke a test and assert the results.
        sdkTester.assertSdkTestRunPasses("testExample");

        // You can optionally provide parameters to your
        // tests for setup.
        Bundle params = new Bundle();
        params.put("someParameter", "A value");
        sdkTester.assertSdkTestRunPasses("testExampleWithParam", params);
    }
}
```

## Custom test instances

The `SdkSandboxTestScenarioRunner` supports invoking tests on different
test instances from the class running your tests inside the test SDK.
For example, you may have tests you wish to also run in a non SDK context.

```java
// You can define tests inside a separate class.
public class ActuallyHasTests {
    @Test
    public void someTest() {
        assertTrue(true);
    }
}

public class ExampleSandboxTestSdk extends SdkSandboxTestScenarioRunner {
    // And then optionally override the onLoadSdk method and use
    // the API setTestInstance to set an instance to invoke tests from.
    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        setTestInstance(new ActuallyHasTests());
        return super.onLoadSdk(params);
    }
}
```

## Custom setup

There may be times when you need to pass through custom setup information to your sdk.

You can optionally provide a Bundle
(https://developer.android.com/reference/android/os/Bundle) to SdkSandboxScenarioRule
that can be retrieved and used from inside test SDKs via the onLoadSdk method.

One example is if you want to reuse an sdk in order to test multiple test instances. For this you
could pass setup information to determine the specific test instance to use:

```java
public class ExampleSandboxTestSdk extends SdkSandboxTestScenarioRunner {

    private ExampleParentTestClass mTestInstance;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        Bundle setupParams = params.getBundle(ISdkSandboxTestExecutor.TEST_SETUP_PARAMS);
        if (setupParams != null) {
            //logic for setting mTestInstance based on params
            mTestInstance = new ExampleChildTestClass();
        }

        setTestInstance(mTestInstance);
        return super.onLoadSdk(params);
    }
}
```

## Custom test binders

There may be times where you want your tests to invoke behavior outside of
the SDK. For example, you may want to perform a tap event using the instrumentation.

You can optionally provide a custom [IBinder]
(https://developer.android.com/reference/android/os/IBinder) to SdkSandboxScenarioRule
that can be retrieved and used from inside test SDKs.

In the example below, the following custom aidl will be used:

```aidl
interface SharedCustomBinder {
    void doesSomethingOutsideSdk();
}
```

```java
public class ExampleSandboxTest {
    // Provide a stub to the SdkSandboxScenarioRule.
    @Rule
    public final SdkSandboxScenarioRule sdkTester = new SdkSandboxScenarioRule(
        "your.test.sdk.package", new SharedCustomBinder.Stub() {
            public void doesSomethingOutsideSdk() {
            }
        });
}

public class ExampleSandboxTestSdk extends SdkSandboxTestScenarioRunner {
    @Test
    public void exampleTest() throws Exception {
        // The IBinder can be retrieved inside SDK tests using the API
        // getCustomInterface().
        SharedCustomBinder binder = SharedCustomBinder.Stub.asInterface(getCustomInterface());
        //
        binder.doesSomethingOutsideSdk();
        // ...
    }
}
```
