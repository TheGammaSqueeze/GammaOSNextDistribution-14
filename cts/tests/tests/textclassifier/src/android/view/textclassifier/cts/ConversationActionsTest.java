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
package android.view.textclassifier.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Person;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.textclassifier.ConversationAction;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.TextClassifier;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ConversationActionsTest {
    private static final String ID = "ID";
    private static final String TEXT = "TEXT";
    private static final Person PERSON = new Person.Builder().setKey(TEXT).build();
    private static final ZonedDateTime TIME =
            ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
    private static final Bundle EXTRAS = new Bundle();
    static {
        EXTRAS.putString(TEXT, TEXT);
    }

    @Test
    public void testMessage_full() {
        ConversationActions.Message message =
                new ConversationActions.Message.Builder(PERSON)
                        .setText(TEXT)
                        .setExtras(EXTRAS)
                        .setReferenceTime(TIME)
                        .build();

        ConversationActions.Message recovered = parcelizeDeparcelize(message,
                ConversationActions.Message.CREATOR);

        assertFullMessage(message);
        assertFullMessage(recovered);
    }

    @Test
    public void testMessage_minimal() {
        ConversationActions.Message message =
                new ConversationActions.Message.Builder(PERSON).build();

        ConversationActions.Message recovered = parcelizeDeparcelize(message,
                ConversationActions.Message.CREATOR);

        assertMinimalMessage(message);
        assertMinimalMessage(recovered);
    }

    @Test
    public void testRequest_minimal() {
        ConversationActions.Message message =
                new ConversationActions.Message.Builder(PERSON)
                        .setText(TEXT)
                        .build();
        ConversationActions.Request request =
                new ConversationActions.Request.Builder(Collections.singletonList(message))
                        .setMaxSuggestions(-1) // The default value.
                        .build();

        ConversationActions.Request recovered =
                parcelizeDeparcelize(request, ConversationActions.Request.CREATOR);

        assertMinimalRequest(request);
        assertMinimalRequest(recovered);
    }

    @Test
    public void testRequest_full() {
        ConversationActions.Message message =
                new ConversationActions.Message.Builder(PERSON)
                        .setText(TEXT)
                        .build();
        TextClassifier.EntityConfig typeConfig =
                new TextClassifier.EntityConfig.Builder()
                        .includeTypesFromTextClassifier(false)
                        .build();
        ConversationActions.Request request =
                new ConversationActions.Request.Builder(Collections.singletonList(message))
                        .setHints(
                                Collections.singletonList(
                                        ConversationActions.Request.HINT_FOR_IN_APP))
                        .setMaxSuggestions(10)
                        .setTypeConfig(typeConfig)
                        .setExtras(EXTRAS)
                        .build();

        ConversationActions.Request recovered =
                parcelizeDeparcelize(request, ConversationActions.Request.CREATOR);

        assertFullRequest(request);
        assertFullRequest(recovered);
    }



    @Test
    public void testConversationActions_full() {
        ConversationAction conversationAction =
                new ConversationAction.Builder(
                        ConversationAction.TYPE_CALL_PHONE)
                        .build();

        ConversationActions conversationActions =
                new ConversationActions(Arrays.asList(conversationAction), ID);

        ConversationActions recovered =
                parcelizeDeparcelize(conversationActions, ConversationActions.CREATOR);

        assertFullConversationActions(conversationActions);
        assertFullConversationActions(recovered);
    }

    @Test
    public void testConversationActions_minimal() {
        ConversationAction conversationAction =
                new ConversationAction.Builder(
                        ConversationAction.TYPE_CALL_PHONE)
                        .build();

        ConversationActions conversationActions =
                new ConversationActions(Arrays.asList(conversationAction), null);

        ConversationActions recovered =
                parcelizeDeparcelize(conversationActions, ConversationActions.CREATOR);

        assertMinimalConversationActions(conversationActions);
        assertMinimalConversationActions(recovered);
    }

    private static void assertFullMessage(ConversationActions.Message message) {
        assertThat(message.getText().toString()).isEqualTo(TEXT);
        assertThat(message.getAuthor()).isEqualTo(PERSON);
        assertThat(message.getExtras().keySet()).containsExactly(TEXT);
        assertThat(message.getReferenceTime()).isEqualTo(TIME);
    }

    private void assertMinimalMessage(ConversationActions.Message message) {
        assertThat(message.getAuthor()).isEqualTo(PERSON);
        assertThat(message.getExtras().isEmpty()).isTrue();
        assertThat(message.getReferenceTime()).isNull();
    }

    private static void assertMinimalRequest(ConversationActions.Request request) {
        assertThat(request.getConversation()).hasSize(1);
        assertThat(request.getConversation().get(0).getText().toString()).isEqualTo(TEXT);
        assertThat(request.getConversation().get(0).getAuthor()).isEqualTo(PERSON);
        assertThat(request.getHints()).isEmpty();
        assertThat(request.getMaxSuggestions()).isEqualTo(-1);
        assertThat(request.getTypeConfig()).isNotNull();
        assertThat(request.getExtras().size()).isEqualTo(0);
    }

    private static void assertFullRequest(ConversationActions.Request request) {
        assertThat(request.getConversation()).hasSize(1);
        assertThat(request.getConversation().get(0).getText().toString()).isEqualTo(TEXT);
        assertThat(request.getConversation().get(0).getAuthor()).isEqualTo(PERSON);
        assertThat(request.getHints()).containsExactly(ConversationActions.Request.HINT_FOR_IN_APP);
        assertThat(request.getMaxSuggestions()).isEqualTo(10);
        assertThat(request.getTypeConfig().shouldIncludeTypesFromTextClassifier()).isFalse();
        assertThat(request.getExtras().keySet()).containsExactly(TEXT);
    }

    private static void assertMinimalConversationActions(ConversationActions conversationActions) {
        assertThat(conversationActions.getConversationActions()).hasSize(1);
        assertThat(conversationActions.getConversationActions().get(0).getType())
                .isEqualTo(ConversationAction.TYPE_CALL_PHONE);
        assertThat(conversationActions.getId()).isNull();
    }

    private static void assertFullConversationActions(ConversationActions conversationActions) {
        assertThat(conversationActions.getConversationActions()).hasSize(1);
        assertThat(conversationActions.getConversationActions().get(0).getType())
                .isEqualTo(ConversationAction.TYPE_CALL_PHONE);
        assertThat(conversationActions.getId()).isEqualTo(ID);
    }

    private static <T extends Parcelable> T parcelizeDeparcelize(
            T parcelable, Parcelable.Creator<T> creator) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return creator.createFromParcel(parcel);
    }
}
