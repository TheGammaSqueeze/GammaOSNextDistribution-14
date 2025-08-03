/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.notification;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the {@link Notification} API. This is to fulfill the API coverage requirement for mainline.
 *
 * <p>Ideally, it should be a CTS. However, it is too late to add a CTS for R, and thus we test it
 * on our side.
 */
@RunWith(JUnit4.class)
public final class NotificationTest {

  private Context context;

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void getMessagesFromBundleArray() {
    Person sender = new Person.Builder().setName("Sender").build();
    Notification.MessagingStyle.Message firstExpectedMessage =
        new Notification.MessagingStyle.Message("hello", /* timestamp= */ 123, sender);
    Notification.MessagingStyle.Message secondExpectedMessage =
        new Notification.MessagingStyle.Message("hello2", /* timestamp= */ 456, sender);

    Notification.MessagingStyle messagingStyle =
        new Notification.MessagingStyle("self name")
            .addMessage(firstExpectedMessage)
            .addMessage(secondExpectedMessage);
    Notification notification =
        new Notification.Builder(context, "test id")
            .setSmallIcon(1)
            .setContentTitle("test title")
            .setStyle(messagingStyle)
            .build();

    List<Notification.MessagingStyle.Message> actualMessages =
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(
            notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES));

    assertThat(actualMessages).hasSize(2);
    assertMessageEquals(firstExpectedMessage, actualMessages.get(0));
    assertMessageEquals(secondExpectedMessage, actualMessages.get(1));
  }

  private static void assertMessageEquals(
      Notification.MessagingStyle.Message expected, Notification.MessagingStyle.Message actual) {
    assertThat(actual.getText().toString()).isEqualTo(expected.getText().toString());
    assertThat(actual.getTimestamp()).isEqualTo(expected.getTimestamp());
    assertThat(actual.getSenderPerson()).isEqualTo(expected.getSenderPerson());
  }
}
