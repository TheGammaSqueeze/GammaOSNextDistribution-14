/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CarTaskViewControllerHostLifecycleFactoryTest {
    private final FakeCarTaskViewControllerHostLifecycleObserver mObserver =
            new FakeCarTaskViewControllerHostLifecycleObserver();
    private TestActivity mActivity;

    @Rule
    public ActivityScenarioRule<TestActivity> activityRule = new ActivityScenarioRule<>(
            TestActivity.class);

    @Before
    public void setup() {
        activityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @Test
    public void forActivity_activityResumed_callsObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle =
                CarTaskViewControllerHostLifecycleFactory.forActivity(mActivity);
        lifecycle.registerObserver(mObserver);

        // Act
        activityRule.getScenario().moveToState(Lifecycle.State.CREATED);
        activityRule.getScenario().moveToState(Lifecycle.State.RESUMED);

        // Assert
        assertThat(mObserver.mHostAppearedCalled).isTrue();
    }

    @Test
    public void forActivity_activityStarted_callsObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle =
                CarTaskViewControllerHostLifecycleFactory.forActivity(mActivity);
        lifecycle.registerObserver(mObserver);

        // Act
        activityRule.getScenario().moveToState(Lifecycle.State.CREATED);
        activityRule.getScenario().moveToState(Lifecycle.State.STARTED);

        // Assert
        assertThat(mObserver.mHostAppearedCalled).isTrue();
    }

    @Test
    public void forActivity_activityStopped_callsObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle =
                CarTaskViewControllerHostLifecycleFactory.forActivity(mActivity);
        lifecycle.registerObserver(mObserver);

        // Act
        activityRule.getScenario().moveToState(Lifecycle.State.RESUMED);
        activityRule.getScenario().moveToState(Lifecycle.State.CREATED);

        // Assert
        assertThat(mObserver.mHostDisappearedCalled).isTrue();
    }

    @Test
    public void forActivity_activityDestroyed_callsObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle =
                CarTaskViewControllerHostLifecycleFactory.forActivity(mActivity);
        lifecycle.registerObserver(mObserver);

        // Act
        activityRule.getScenario().moveToState(Lifecycle.State.CREATED);
        activityRule.getScenario().moveToState(Lifecycle.State.DESTROYED);

        // Assert
        assertThat(mObserver.mHostDestroyedCalled).isTrue();
    }

    public static class TestActivity extends Activity {
    }
}
