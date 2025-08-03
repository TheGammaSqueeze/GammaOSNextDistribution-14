/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.textclassifier.ConversationAction;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ConversationActionTest {
    private static final String TEXT = "TEXT";
    private static final float FLOAT_TOLERANCE = 0.01f;
    private static final PendingIntent PENDING_INTENT = PendingIntent.getActivity(
            InstrumentationRegistry.getTargetContext(),
            0,
            new Intent(),
            PendingIntent.FLAG_IMMUTABLE);
    private static final RemoteAction REMOTE_ACTION = new RemoteAction(
            Icon.createWithData(new byte[0], 0, 0),
            TEXT,
            TEXT,
            PENDING_INTENT);
    private static final Bundle EXTRAS = new Bundle();
    static {
        EXTRAS.putString(TEXT, TEXT);
    }

    @Test
    public void testConversationAction_minimal() {
        ConversationAction conversationAction =
                new ConversationAction.Builder(
                        ConversationAction.TYPE_CALL_PHONE)
                        .build();

        ConversationAction recovered =
                parcelizeDeparcelize(conversationAction,
                        ConversationAction.CREATOR);

        assertMinimalConversationAction(conversationAction);
        assertMinimalConversationAction(recovered);
    }

    @Test
    public void testConversationAction_full() {
        ConversationAction conversationAction =
                new ConversationAction.Builder(
                        ConversationAction.TYPE_CALL_PHONE)
                        .setConfidenceScore(1.0f)
                        .setTextReply(TEXT)
                        .setAction(REMOTE_ACTION)
                        .setExtras(EXTRAS)
                        .build();

        ConversationAction recovered =
                parcelizeDeparcelize(conversationAction,
                        ConversationAction.CREATOR);

        assertFullConversationAction(conversationAction);
        assertFullConversationAction(recovered);
    }

    private static void assertMinimalConversationAction(
            ConversationAction conversationAction) {
        assertThat(conversationAction.getAction()).isNull();
        assertThat(conversationAction.getConfidenceScore()).isWithin(FLOAT_TOLERANCE).of(0.0f);
        assertThat(conversationAction.getType()).isEqualTo(ConversationAction.TYPE_CALL_PHONE);
    }

    private static void assertFullConversationAction(
            ConversationAction conversationAction) {
        assertThat(conversationAction.getAction().getTitle()).isEqualTo(TEXT);
        assertThat(conversationAction.getConfidenceScore()).isWithin(FLOAT_TOLERANCE).of(1.0f);
        assertThat(conversationAction.getType()).isEqualTo(ConversationAction.TYPE_CALL_PHONE);
        assertThat(conversationAction.getTextReply()).isEqualTo(TEXT);
        assertThat(conversationAction.getExtras().keySet()).containsExactly(TEXT);
    }

    private static <T extends Parcelable> T parcelizeDeparcelize(
            T parcelable, Parcelable.Creator<T> creator) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return creator.createFromParcel(parcel);
    }
}
