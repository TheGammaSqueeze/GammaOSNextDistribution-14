/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.cts;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.test.AndroidTestCase;
import android.util.Rational;

import java.util.ArrayList;
import java.util.List;

public class PictureInPictureParamsTest extends AndroidTestCase {

    /**
     * Tests that we get the same values back from the public PictureInPicture params getters that
     * were set via the PictureInPictureParams.Builder.
     */
    public void testPictureInPictureParamsGetters() {
        ArrayList<RemoteAction> actions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            actions.add(createRemoteAction(0));
        }

        assertPictureInPictureParamsGettersMatchValues(
                actions,
                createRemoteAction(1),
                new Rational(1, 2),
                new Rational(100, 1),
                "Title",
                "Subtitle",
                new Rect(0, 0, 100, 100),
                true,
                true);
    }

    public void testPictureInPictureParamsGettersNullValues() {
        assertPictureInPictureParamsGettersMatchValues(null, null, null, null, null, null, null,
                false, false);
    }

    private void assertPictureInPictureParamsGettersMatchValues(List<RemoteAction> actions,
            RemoteAction closeAction, Rational aspectRatio, Rational expandedAspectRatio,
            String title, String subtitle, Rect sourceRectHint, boolean isAutoEnterEnabled,
            boolean isSeamlessResizeEnabled) {

        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setActions(actions)
                .setCloseAction(closeAction)
                .setAspectRatio(aspectRatio)
                .setExpandedAspectRatio(expandedAspectRatio)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setSourceRectHint(sourceRectHint)
                .setAutoEnterEnabled(isAutoEnterEnabled)
                .setSeamlessResizeEnabled(isSeamlessResizeEnabled)
                .build();

        if (actions == null) {
            assertEquals(new ArrayList<>(), params.getActions());
        } else {
            assertEquals(actions, params.getActions());
        }
        assertEquals(closeAction, params.getCloseAction());
        assertEquals(aspectRatio, params.getAspectRatio());
        assertEquals(expandedAspectRatio, params.getExpandedAspectRatio());
        assertEquals(title, params.getTitle());
        assertEquals(subtitle, params.getSubtitle());
        assertEquals(sourceRectHint, params.getSourceRectHint());
        assertEquals(isAutoEnterEnabled, params.isAutoEnterEnabled());
        assertEquals(isSeamlessResizeEnabled, params.isSeamlessResizeEnabled());
    }

    /** @return {@link RemoteAction} instance titled after a given index */
    private RemoteAction createRemoteAction(int index) {
        return new RemoteAction(
                Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)),
                "action " + index,
                "contentDescription " + index,
                PendingIntent.getBroadcast(getContext(), 0, new Intent(),
                        PendingIntent.FLAG_IMMUTABLE));
    }
}
