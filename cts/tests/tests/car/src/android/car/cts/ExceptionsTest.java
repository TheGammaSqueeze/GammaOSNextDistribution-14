/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import android.car.CarNotConnectedException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExceptionsTest extends AbstractCarLessTestCase {

    private static final String MESSAGE = "Oops!";
    private static final Exception CAUSE = new RuntimeException();

    @Test
    public void testCarNotConnectedException() {
        CarNotConnectedException exception = new CarNotConnectedException();
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();

        exception = new CarNotConnectedException(MESSAGE);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE);
        assertThat(exception.getCause()).isNull();

        exception = new CarNotConnectedException(MESSAGE, CAUSE);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE);
        assertThat(exception.getCause()).isSameInstanceAs(CAUSE);

        exception = new CarNotConnectedException(CAUSE);
        assertThat(exception.getCause()).isSameInstanceAs(CAUSE);
    }
}
