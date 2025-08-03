# Shared Webkit Environment

## Overview

This helper lib makes a test suite extendable to run in both an activity based environment and
within the SDK Runtime.

[design](go/shared-sdk-sandbox-webview-tests) (*only visible to Googlers)

## Expected prior knowledge

Read the test scenario documentation to get a better understanding of how we invoke tests inside
the SDK Runtime:
`//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/README.md`

## Tutorial

*** aside
**Tip:**  If your test suite already extends SharedWebViewTest, you can skip to section
3, "Converting a test to shared"
***

### 1. Making a test suite sharable with the SDK Runtime

If you want to share webkit tests inside the SDK runtime, you will need to make your
test suite inherit from `android.webkit.cts.SharedWebViewTest`. This is used to indicate
that a test suite has a configurable environment.

Eg:
```java
- public class WebViewTest
+ public class WebViewTest extends SharedWebViewTest
```

*** aside
**Note:**  Some WebView tests still use the JUnit 3 style, so you may need to
first migrate the test suite from `ActivityInstrumentationTestCase2` to use
`ActivityScenarioRule` (which is for JUnit 4 style). See
[b/112773416](http://b/112773416) for details.
***

This abstract class requires you to implement the method `createTestEnvironment` that
defines the test environment for your test suite. Think of the test environment as a
concrete description of where this test suite will execute. `createTestEnvironment` should
have all the references your test has to an activity. This will be overridden later on by the
SDK tests using the API method `setTestEnvironment`.

Eg:
```java
@Override
protected SharedWebViewTestEnvironment createTestEnvironment() {
    return new SharedWebViewTestEnvironment.Builder()
            .setContext(mActivity)
            .setWebView(mWebView)
            // This allows SDK methods to invoke APIs from outside the SDK.
            // The Activity based tests don't need to worry about this so you can
            // just provide the host app invoker directly to this environment.
            .setHostAppInvoker(SharedWebViewTestEnvironment.createHostAppInvoker())
            .build();
}
```

Your test suite is now sharable with an SDK runtime test suite!

### 2. Sharing your tests with the SDK Runtime

The SDK Runtime tests for webkit live under `//cts/tests/tests/sdksandbox/webkit`.

You need a test SDK that will actually have your tests, and a JUnit test suite that JUnit will have to invoke your tests from an activity.

You can follow the "Creating new SDK Runtime tests" section under the SDK testscenario
guide (store these SDK tests in `//cts/tests/tests/sdksandbox/webkit`):
`//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/README.md`

*** aside
**Note:**  If you reuse the WebViewSandboxTestSdk below you will only need to follow the last step of the "Invoke from a JUnit test suite" section from the guide above.
***

However, instead of creating a new test SDK as per the guide above, you can reuse the WebViewSandboxTestSdk
`//cts/tests/tests/sdksandbox/webkit/sdksidetests/WebViewSandboxTest/src/com/android/cts/sdksidetests/webviewsandboxtest/WebViewSandboxTestSdk.java`
To do this use the `android.sdksandbox.webkit.cts.WebViewSandboxTestRule` and pass in the fully qualified name of your test class.

Congratulations! Your webkit tests are now shared with your SDK Runtime tests!

### 3. Converting a test to shared

You need to do two things when you are making a test shared:
1. Update the test suite to use the `SharedWebViewTestEnvironment`
2. Update the SDK JUnit Test Suite to invoke the test

We will use `WebViewTest` as an example:
`//cts/tests/tests/webkit/src/android/webkit/cts/WebViewTest.java`

Search for `getTestEnvironment()`. This method returns a `SharedWebViewTestEnvironment`.
Whenever your test needs to refer to anything that is not available in the SDK runtime,
or needs to be shared between the SDK runtime and the activity based tests,
use this class.

Open `SharedWebViewTestEnvironment` to familiarize yourself with what is available:
`//cts/libs/webkit-shared/src/android/webkit/cts/SharedWebViewTestEnvironment.java`

First convert any direct references to any variable that should come from the shared test
environment.

Eg:
```java
@Test
public void testExample() throws Throwable {
    - InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    + getTestEnvironment().waitForIdleSync();
    ...
}
```

*** note
**Tip:**  You can likely just update your setup method to pull from the test environment for
minimal refactoring. Eg:

```java
@Test
public void setup() {
    mWebView = getTestEnvironment().getWebView();
    ...
}
```
***

Next you will invoke this shared test from your SDK JUnit test suite. An example of a JUnit
test suite can be found here:
`//cts/tests/tests/sdksandbox/webkit/src/android/sdksandbox/webkit/cts/WebViewSandboxTest.java`

You can see in this file that we use `SdkSandboxScenarioRule#assertSdkTestRunPasses` to invoke
test methods.

And that's it! Your test should now run! You can test that your method was added with `atest`:

```sh
# Confirm the test runs in the sandbox
atest CtsSdkSandboxWebkitTestCases:WebViewSandboxTest#<shared_test>
# Confirm the test still runs normally
atest CtsWebkitTestCases:WebViewTest#<shared_test>
```

## Invoking behavior in the Activity

There are some things you won't be able to initiate from within the SDK runtime
that are needed to write tests. For example, you cannot start a local server
in the SDK runtime, but this would be useful for testing against.

You can use the
ActivityInvoker (`//cts/libs/webkit-shared/src/android/webkit/cts/IActivityInvoker.aidl`)
to add this functionality.
The activity invoker allows SDK runtime tests to initiate events in the activity driving
the tests.

Once you have added a new ActivityInvoker API, provide a wrapper API to SharedWebViewTestEnvironment
to abstract these APIs away from test authors.
