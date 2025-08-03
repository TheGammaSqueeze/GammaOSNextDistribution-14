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
package com.google.android.enterprise.connectedapps;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for the {@link Permissions} class running on unsupported Android versions. */
@RunWith(RobolectricTestRunner.class)
@Config(maxSdk = VERSION_CODES.N_MR1)
public class PermissionsUnsupportedTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final ConnectionBinder binder = new DefaultProfileBinder();
  private final Permissions permissions = new PermissionsImpl(context, binder);

  @Test
  public void canMakeCrossProfileCalls_unsupportedVersion_returnsFalse() {
    assertThat(permissions.canMakeCrossProfileCalls()).isFalse();
  }
}
