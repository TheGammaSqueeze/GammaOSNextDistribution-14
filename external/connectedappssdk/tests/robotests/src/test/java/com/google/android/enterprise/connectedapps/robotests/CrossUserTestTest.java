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
package com.google.android.enterprise.connectedapps.robotests;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.testapp.crossuser.AppCrossUserConfiguration;
import com.google.android.enterprise.connectedapps.testapp.crossuser.FakeAppCrossUserConnector;
import com.google.android.enterprise.connectedapps.testapp.crossuser.FakeUserNotesManager;
import com.google.android.enterprise.connectedapps.testapp.crossuser.NotesManager;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossUserTest;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@CrossUserTest(configuration = AppCrossUserConfiguration.class)
@RunWith(RobolectricTestRunner.class)
public class CrossUserTestTest {

  private static final String NOTE_1 = "I stayed at home today";
  private static final String NOTE_2 = "I am hungry";
  private static final String NOTE_3 = "I should eat something, probably";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService executor = new TestScheduledExecutorService();
  private final RobolectricTestUtilities utilities =
      new RobolectricTestUtilities(context, executor);

  private final UserHandle user4Handle = utilities.createCustomUser(4);
  private final UserHandle user5Handle = utilities.createCustomUser(5);

  private final NotesManager user4NotesManager = new NotesManager();
  private final NotesManager user5NotesManager = new NotesManager();

  private final FakeAppCrossUserConnector connector = new FakeAppCrossUserConnector(context);
  private final FakeUserNotesManager defaultFakeUserNotesManager =
      FakeUserNotesManager.builder()
          .user(user4Handle, user4NotesManager)
          .user(user5Handle, user5NotesManager)
          .connector(connector)
          .build();

  @Before
  public void setUp() {
    connector.setRunningOnUser(user4Handle);
    connector.turnOnUser(user5Handle);
  }

  @Test
  public void currentCall_returnsCorrectNotesManager()
      throws ExecutionException, InterruptedException {
    user4NotesManager.addNote(NOTE_1);
    user4NotesManager.addNote(NOTE_2);
    user5NotesManager.addNote(NOTE_3);

    ListenableFuture<Set<String>> currentUserNotesFuture =
        defaultFakeUserNotesManager.current().getNotesFuture();

    assertThat(currentUserNotesFuture.get()).containsExactly(NOTE_1, NOTE_2);
  }

  @Test
  public void userCall_returnsCorrectNotesManager()
      throws ExecutionException, InterruptedException {
    user4NotesManager.addNote(NOTE_1);
    user5NotesManager.addNote(NOTE_2);
    user5NotesManager.addNote(NOTE_3);

    ListenableFuture<Set<String>> user5NotesFuture =
        defaultFakeUserNotesManager.user(user5Handle).getNotesFuture();

    assertThat(user5NotesFuture.get()).containsExactly(NOTE_2, NOTE_3);
  }

  @Test
  public void currentCall_runningUserNull_throwsException() {
    connector.setRunningOnUser(null);

    assertThrows(UnsupportedOperationException.class, defaultFakeUserNotesManager::current);
  }

  @Test
  public void currentCall_noTargetType_throwsException() {
    FakeUserNotesManager noTargetTypeNotesManager =
        FakeUserNotesManager.builder().connector(connector).build();

    assertThrows(UnsupportedOperationException.class, noTargetTypeNotesManager::current);
  }

  @Test
  public void userCall_userHandleNull_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> defaultFakeUserNotesManager.user(null));
  }

  @Test
  public void userCall_noTargetType_throwsException() {
    FakeUserNotesManager noTargetTypeNotesManager =
        FakeUserNotesManager.builder().connector(connector).build();

    assertThrows(
        UnsupportedOperationException.class, () -> noTargetTypeNotesManager.user(user4Handle));
  }

  @Test
  public void userCall_userTurnedOff_throwsException() {
    connector.turnOffUser(user5Handle);

    assertThrows(
        ExecutionException.class,
        () -> defaultFakeUserNotesManager.user(user5Handle).getNotesFuture().get());
  }

  @Test
  public void builder_noConnector_throws() {
    assertThrows(IllegalStateException.class, () -> FakeUserNotesManager.builder().build());
  }
}
