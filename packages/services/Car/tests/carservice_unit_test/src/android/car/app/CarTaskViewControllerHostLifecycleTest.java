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

import org.junit.Test;

public class CarTaskViewControllerHostLifecycleTest {
    @Test
    public void hostAppeared_notifiesObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle = new CarTaskViewControllerHostLifecycle();
        FakeCarTaskViewControllerHostLifecycleObserver observer =
                new FakeCarTaskViewControllerHostLifecycleObserver();
        lifecycle.registerObserver(observer);

        // Act
        lifecycle.hostAppeared();

        // Assert
        assertThat(observer.mHostAppearedCalled).isTrue();
    }

    @Test
    public void hostAppeared_isVisibleReturnsTrue() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle = new CarTaskViewControllerHostLifecycle();

        // Act
        lifecycle.hostAppeared();

        // Assert
        assertThat(lifecycle.isVisible()).isTrue();
    }

    @Test
    public void hostDisappeared_notifiesObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle = new CarTaskViewControllerHostLifecycle();
        FakeCarTaskViewControllerHostLifecycleObserver observer =
                new FakeCarTaskViewControllerHostLifecycleObserver();
        lifecycle.registerObserver(observer);

        // Act
        lifecycle.hostDisappeared();

        // Assert
        assertThat(observer.mHostDisappearedCalled).isTrue();
    }

    @Test
    public void hostDisappeared_isVisibleReturnsFalse() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle = new CarTaskViewControllerHostLifecycle();
        lifecycle.hostAppeared();

        // Act
        lifecycle.hostDisappeared();

        // Assert
        assertThat(lifecycle.isVisible()).isFalse();
    }

    @Test
    public void hostDestroyed_notifiesObserver() {
        // Arrange
        CarTaskViewControllerHostLifecycle lifecycle = new CarTaskViewControllerHostLifecycle();
        FakeCarTaskViewControllerHostLifecycleObserver observer =
                new FakeCarTaskViewControllerHostLifecycleObserver();
        lifecycle.registerObserver(observer);

        // Act
        lifecycle.hostDestroyed();

        // Assert
        assertThat(observer.mHostDestroyedCalled).isTrue();
    }
}
