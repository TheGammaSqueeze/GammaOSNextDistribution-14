package com.android.myroboapplication;

import static org.junit.Assert.assertEquals;
import static com.android.myroboapplication.R.*;

import android.content.Intent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
public class WelcomeActivityTest {

    @Before
    public void setup() throws Exception {}

    @Test
    public void clickingLogin_shouldStartLoginActivity() throws Exception {
        ActivityController<WelcomeActivity> controller =
                Robolectric.buildActivity(WelcomeActivity.class);
        controller.setup(); // Moves Activity to RESUMED state
        WelcomeActivity activity = controller.get();

        activity.findViewById(R.id.login).performClick();
        Intent expectedIntent = new Intent(activity, LoginActivity.class);
        Intent actual = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals(expectedIntent.getComponent(), actual.getComponent());
    }
}
