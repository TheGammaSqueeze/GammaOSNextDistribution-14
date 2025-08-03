/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.testing;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.FakeTestProfileConnector;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Test specifics of a single generated fake for {@link
 * com.google.android.enterprise.connectedapps.ProfileConnector}
 *
 * <p>More extensive tests of this functionality are in {@link AbstractFakeProfileConnectorTest}.
 */
@CrossProfileTest(configuration = TestApplication.class)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class GeneratedFakeProfileConnectorTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  private final FakeTestProfileConnector fakeTestProfileConnector =
      new FakeTestProfileConnector(context);

  @Test
  public void getPrimaryProfile_equalsWorkProfile() {
    // The TestProfileConnector's primary profile is set to work
    assertThat(fakeTestProfileConnector.utils().getPrimaryProfile())
        .isEqualTo(fakeTestProfileConnector.utils().getWorkProfile());
  }
}
