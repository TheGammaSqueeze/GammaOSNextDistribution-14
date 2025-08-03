/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.debuggingrestrictioncontroller;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertThat;

import android.app.Activity;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.contrib.ActivityResultMatchers;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.car.debuggingrestrictioncontroller.ui.token.TokenActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TokenTest {

  private static final String TEST_EMAIL = BuildConfig.DRC_TEST_EMAIL;
  private static final String TEST_PASSWORD = BuildConfig.DRC_TEST_PASSWORD;
  private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

  @Rule
  public ActivityScenarioRule<TokenActivity> activityScenarioRule =
      new ActivityScenarioRule<TokenActivity>(TokenActivity.class);

  private CountingIdlingResource idlingResource;

  public TokenTest() throws ExecutionException, InterruptedException {
    Tasks.await(firebaseAuth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD));
  }

  @Before
  public void setUp() {
    activityScenarioRule
        .getScenario()
        .onActivity(
            activity -> {
              idlingResource = activity.getIdlingResource();
            });
    IdlingRegistry.getInstance().register(idlingResource);
  }

  @After
  public void tearDown() {
    IdlingRegistry.getInstance().unregister(idlingResource);
    firebaseAuth.signOut();
  }

  @Test
  public void initialButtonStates() {
    onView(withId(R.id.agreement)).check(matches(isDisplayed()));
    onView(withId(R.id.agree)).check(matches(isDisplayed()));
    onView(withId(R.id.agree)).check(matches(isEnabled()));
    onView(withId(R.id.disagree)).check(matches(isDisplayed()));
  }

  @Test
  public void agree() {
    onView(withId(R.id.agree)).check(matches(isEnabled()));
    onView(withId(R.id.agree)).perform(click());
    assertThat(
        activityScenarioRule.getScenario().getResult(),
        ActivityResultMatchers.hasResultCode(Activity.RESULT_OK));
  }

  @Test
  public void disagree() {
    onView(withId(R.id.disagree)).check(matches(isEnabled()));
    onView(withId(R.id.disagree)).perform(click());
    assertThat(
        activityScenarioRule.getScenario().getResult(),
        ActivityResultMatchers.hasResultCode(Activity.RESULT_CANCELED));
  }

  @Test
  public void userNotSignedIn() {
    firebaseAuth.signOut();
    onView(withId(R.id.agree)).perform(click());
    assertThat(
        activityScenarioRule.getScenario().getResult(),
        ActivityResultMatchers.hasResultCode(Activity.RESULT_CANCELED));
  }
}
