/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.android.testing.testinstallin;

import static com.google.common.truth.Truth.assertThat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.ActivityBarModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.ActivityFooTestModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.ActivityLevel;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Bar;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Foo;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.FragmentBarModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.FragmentFooTestModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.FragmentLevel;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.SingletonBarModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.SingletonFooTestModule;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

// Test that Foo uses the global @TestInstallIn module and Bar uses the global @InstallIn module.
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class TestInstallInFooTest {

  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Inject Foo foo;
  @Inject Bar bar;

  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_TestInstallInFooTest_TestActivity {
    @Inject @ActivityLevel Foo foo;
    @Inject @ActivityLevel Bar bar;
  }

  @AndroidEntryPoint(Fragment.class)
  public static final class TestFragment extends Hilt_TestInstallInFooTest_TestFragment {
    @Inject @FragmentLevel Foo foo;
    @Inject @FragmentLevel Bar bar;
  }

  @Test
  public void testSingletonFooUsesTestInstallIn() {
    hiltRule.inject();
    assertThat(foo.moduleClass).isEqualTo(SingletonFooTestModule.class);
  }

  @Test
  public void testSingletonBarUsesInstallIn() {
    hiltRule.inject();
    assertThat(bar.moduleClass).isEqualTo(SingletonBarModule.class);
  }

  @Test
  public void testActivityFooUsesTestInstallIn() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> assertThat(activity.foo.moduleClass).isEqualTo(ActivityFooTestModule.class));
    }
  }

  @Test
  public void testActivityBarUsesInstallIn() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> assertThat(activity.bar.moduleClass).isEqualTo(ActivityBarModule.class));
    }
  }

  @Test
  public void testFragmentFooUsesTestInstallIn() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> assertThat(getTestFragment(activity).foo.moduleClass)
              .isEqualTo(FragmentFooTestModule.class));
    }
  }

  @Test
  public void testFragmentBarUsesInstallIn() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> assertThat(getTestFragment(activity).bar.moduleClass)
              .isEqualTo(FragmentBarModule.class));
    }
  }

  private TestFragment getTestFragment(FragmentActivity activity) {
    TestFragment fragment = new TestFragment();
    activity
        .getSupportFragmentManager()
        .beginTransaction()
        .add(fragment, null)
        .commitNow();
    return fragment;
  }
}
