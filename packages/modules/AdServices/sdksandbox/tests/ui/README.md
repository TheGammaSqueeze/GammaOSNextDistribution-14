# SDK Sandbox UI Tests

## Overview

The SDK Sandbox UI test suite facilitates the testing of the UI components of the SDK sandbox, such
as the rendering of remote views into a `SurfaceView`. This test suite enables the testing of UI
functionality on multiple device specifications, which may be configured on a per-class basis.

These tests make use of the `SdkSandboxUiTestRule` rule, which includes a screenshot
asserter. This asserter may be used to compare portions of the emulated display with "golden"
images from the `assets` directory. The screenshot asserter uses an `AlmostPerfectMatcher` to
ensure that the rendered views are indistinguishable from the expected golden images (allowing a
very small tolerance to allow for rendering differences on different devices).

As part of the `SdkSandboxUiTestRule`, an SDK is loaded in the test app. The loaded SDK
may be configured on a per-class basis, by passing the name of the `SandboxedSdkProvider` to the
`SdkSandboxUiTestRule`. If the test needs to interact with the SDK's `SandboxedSdk` object, this
can be done by calling the `getSandboxedSdk()` method of the test rule.

## Rendering Remote Views
Remote views may be loaded by using `SdkSandboxUiTestRule.renderInView()`. This will render a view
of size `width` and `height` in pixels. The view will contain the drawable defined by the passed
resource ID.

## Interacting with Activities

To test UI interactions such as clicking and scrolling, or to locate a view on screen, it will
sometimes be necessary for the test to interact with the test activity. This can be done
by calling `getActivityScenario()` on the test rule. The test app will be launched with the default
activity class, which may be specified in the constructor of the test rule.
The activity may be switched by calling `switchActivity(Activity activity)`.


```java
public class ExampleTest {
    SdkSandboxUiRule mRule;

    @Test
    public void testActivities() {
        // Default activity is current running
        mRule.getActivityScenario().onActivity(activity -> {
            // interact with activity
        });
        mRule.switchActivity(NewActivity.class);
        // NewActivity is now running
        mRule.getActivityScenario().onActivity(activity -> {
            // interact with new activity
        });
    }
}
```

## Input Injection
Due to `SurfaceView` restrictions, `MotionEvents` cannot be programmatically injected into remotely
rendered views. Instead, the input event can be simulated using the test instrumentation.
For example, to simulate a click we can find the x and y co-ordinates of the view on screen, and
send `MOTION_DOWN` and `MOTION_UP` events to the test instrumentation at these co-ordinates.
