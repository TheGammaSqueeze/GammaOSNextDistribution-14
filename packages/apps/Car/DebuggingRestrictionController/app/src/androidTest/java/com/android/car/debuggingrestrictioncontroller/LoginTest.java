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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.toPackage;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.car.debuggingrestrictioncontroller.ui.login.LoginActivity;
import com.android.car.debuggingrestrictioncontroller.ui.token.TokenActivity;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoginTest {

  private static final String TEST_EMAIL = BuildConfig.DRC_TEST_EMAIL;
  private static final String TEST_PASSWORD = BuildConfig.DRC_TEST_PASSWORD;
  private static final String INVALID_EMAIL = "invalid_email@";
  private static final String SHORT_PASSWORD = "word";
  private static final String WRONG_PASSWORD = "wrong_password";
  private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

  @Rule
  public ActivityScenarioRule<LoginActivity> activityScenarioRule =
      new ActivityScenarioRule<LoginActivity>(LoginActivity.class);

  private CountingIdlingResource idlingResource;

  @Before
  public void setUp() {
    activityScenarioRule
        .getScenario()
        .onActivity(
            activity -> {
              idlingResource = activity.getIdlingResource();
            });
    IdlingRegistry.getInstance().register(idlingResource);
    Intents.init();
    firebaseAuth.signOut();
  }

  @After
  public void tearDown() {
    IdlingRegistry.getInstance().unregister(idlingResource);
    Intents.release();
    firebaseAuth.signOut();
  }

  @Test
  public void invalidEmail() {
    Context ctx = getApplicationContext();
    onView(withId(R.id.username)).perform(typeText(INVALID_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.username))
        .check(matches(hasErrorText(ctx.getString(R.string.invalid_username))));
    onView(withId(R.id.login)).check(matches(not(isEnabled())));
  }

  @Test
  public void invalidPassword() {
    Context ctx = getApplicationContext();
    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password))
        .perform(typeText(SHORT_PASSWORD), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password))
        .check(matches(hasErrorText(ctx.getString(R.string.invalid_password))));
    onView(withId(R.id.login)).check(matches(not(isEnabled())));
  }

  @Test
  public void initialButtonStates() {
    onView(withId(R.id.login)).check(matches(isDisplayed()));
    onView(withId(R.id.login)).check(matches(not(isEnabled())));
    onView(withId(R.id.next)).check(matches(isDisplayed()));
    onView(withId(R.id.next)).check(matches(not(isEnabled())));
  }

  @Test
  public void wrongPassword() {
    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password))
        .perform(typeText(WRONG_PASSWORD), ViewActions.pressImeActionButton());
    onView(withId(R.id.next)).check(matches(not(isEnabled())));
    firebaseAuth.signOut();
  }

  @Test
  public void userLogout() {
    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password)).perform(typeText(TEST_PASSWORD), ViewActions.closeSoftKeyboard());

    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).check(matches(withText(R.string.button_sign_in)));
    onView(withId(R.id.login)).perform(click());
    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).check(matches(withText(R.string.button_sign_out)));
    onView(withId(R.id.login)).perform(click());
    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).check(matches(withText(R.string.button_sign_in)));
    firebaseAuth.signOut();
  }

  @Test
  public void startTokenActivity() {
    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password)).perform(typeText(TEST_PASSWORD), ViewActions.closeSoftKeyboard());

    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).perform(click());
    onView(withId(R.id.next)).check(matches(isEnabled()));
    onView(withId(R.id.next)).perform(click());

    intended(
        allOf(
            toPackage(getApplicationContext().getPackageName()),
            hasComponent(TokenActivity.class.getName())));
    onView(withId(R.id.agreement)).check(matches(isDisplayed()));
    onView(withId(R.id.agree)).check(matches(isDisplayed()));
    onView(withId(R.id.disagree)).check(matches(isDisplayed()));
    firebaseAuth.signOut();
  }

  @Test
  public void returnedFromTokenActivityOK() {
    Intent intent = new Intent(getApplicationContext(), TokenActivity.class);
    ActivityResult result = new ActivityResult(Activity.RESULT_OK, intent);
    intending(
            allOf(
                toPackage(getApplicationContext().getPackageName()),
                hasComponent(TokenActivity.class.getName())))
        .respondWith(result);

    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password)).perform(typeText(TEST_PASSWORD), ViewActions.closeSoftKeyboard());

    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).perform(click());
    onView(withId(R.id.next)).check(matches(isEnabled()));
    onView(withId(R.id.next)).perform(click());

    onView(withId(com.google.android.material.R.id.snackbar_text))
        .check(matches(withText(R.string.token_authorized)));
    firebaseAuth.signOut();
  }

  @Test
  public void returnedFromTokenActivityCancelled() {
    Intent intent = new Intent(getApplicationContext(), TokenActivity.class);
    ActivityResult result = new ActivityResult(Activity.RESULT_CANCELED, intent);
    intending(
            allOf(
                toPackage(getApplicationContext().getPackageName()),
                hasComponent(TokenActivity.class.getName())))
        .respondWith(result);

    onView(withId(R.id.username)).perform(typeText(TEST_EMAIL), ViewActions.closeSoftKeyboard());
    onView(withId(R.id.password)).perform(typeText(TEST_PASSWORD), ViewActions.closeSoftKeyboard());

    onView(withId(R.id.login)).check(matches(isEnabled()));
    onView(withId(R.id.login)).perform(click());
    onView(withId(R.id.next)).check(matches(isEnabled()));
    onView(withId(R.id.next)).perform(click());

    onView(withId(com.google.android.material.R.id.snackbar_text))
        .check(matches(withText(R.string.token_unauthorized)));
    firebaseAuth.signOut();
  }
}
