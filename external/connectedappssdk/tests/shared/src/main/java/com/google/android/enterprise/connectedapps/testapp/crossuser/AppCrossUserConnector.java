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
package com.google.android.enterprise.connectedapps.testapp.crossuser;

import android.content.Context;
import com.google.android.enterprise.connectedapps.UserBinderFactory;
import com.google.android.enterprise.connectedapps.UserConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomUserConnector;
import com.google.android.enterprise.connectedapps.annotations.GeneratedUserConnector;
import java.util.concurrent.ScheduledExecutorService;

@GeneratedUserConnector
@CustomUserConnector
public interface AppCrossUserConnector extends UserConnector {
  static AppCrossUserConnector create(Context context) {
    return GeneratedAppCrossUserConnector.builder(context).build();
  }

  static AppCrossUserConnector create(
      Context context, ScheduledExecutorService scheduledExecutorService) {
    return GeneratedAppCrossUserConnector.builder(context)
        .setScheduledExecutorService(scheduledExecutorService)
        .build();
  }

  static AppCrossUserConnector create(
      Context context,
      ScheduledExecutorService scheduledExecutorService,
      UserBinderFactory binderFactory) {
    return GeneratedAppCrossUserConnector.builder(context)
        .setBinderFactory(binderFactory)
        .setScheduledExecutorService(scheduledExecutorService)
        .build();
  }
}
