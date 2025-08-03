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
package android.quickaccesswallet.cts;

import static android.quickaccesswallet.cts.TestUtils.compareIcons;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Parcel;
import android.quickaccesswallet.QuickAccessWalletActivity;
import android.service.quickaccesswallet.WalletCard;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests parceling of the {@link WalletCard}
 */
@RunWith(AndroidJUnit4.class)
public class WalletCardTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * This inserts the key value pair and assert they can be retrieved. Then it clears the
     * preferences and asserts they can no longer be retrieved.
     */
    @Test
    public void testParcel_toParcel() {
        Bitmap bitmap = Bitmap.createBitmap(70, 44, Bitmap.Config.ARGB_8888);
        Bitmap bitmapSecondary = Bitmap.createBitmap(50, 30, Bitmap.Config.ARGB_8888);
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        Location location1 = new Location("test");
        location1.setLatitude(10.0);
        location1.setLongitude(20.0);
        Location location2 = new Location("test");
        location2.setLatitude(15.0);
        location2.setLongitude(25.0);
        WalletCard card =
                new WalletCard.Builder(
                                "cardId",
                                WalletCard.CARD_TYPE_NON_PAYMENT,
                                Icon.createWithBitmap(bitmap),
                                "content description",
                                PendingIntent.getActivity(
                                        mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                        .setCardIcon(
                                Icon.createWithResource(
                                        mContext, android.R.drawable.ic_dialog_info))
                        .setCardLabel("card label")
                        .setNonPaymentCardSecondaryImage(Icon.createWithBitmap(bitmapSecondary))
                        .setCardLocations(List.of(location1, location2))
                        .build();

        Parcel p = Parcel.obtain();
        card.writeToParcel(p, 0);
        p.setDataPosition(0);
        WalletCard newCard = WalletCard.CREATOR.createFromParcel(p);
        assertThat(card.getCardId()).isEqualTo(newCard.getCardId());
        assertThat(card.getCardType()).isEqualTo(newCard.getCardType());
        compareIcons(mContext, card.getCardImage(), newCard.getCardImage());
        assertThat(card.getContentDescription()).isEqualTo(newCard.getContentDescription());
        assertThat(card.getPendingIntent()).isEqualTo(newCard.getPendingIntent());
        compareIcons(mContext, card.getCardIcon(), newCard.getCardIcon());
        assertThat(card.getCardLabel()).isEqualTo(newCard.getCardLabel());
        compareIcons(
                mContext,
                card.getNonPaymentCardSecondaryImage(),
                newCard.getNonPaymentCardSecondaryImage());
        assertThat(card.getCardLocations()).isEqualTo(newCard.getCardLocations());
    }

    @Test
    public void testParcel_noIconOrLabel_toParcel() {
        Bitmap bitmap = Bitmap.createBitmap(70, 44, Bitmap.Config.ARGB_8888);
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        WalletCard card = new WalletCard.Builder(
                "cardId",
                WalletCard.CARD_TYPE_PAYMENT,
                Icon.createWithBitmap(bitmap),
                "content description",
                PendingIntent.getActivity(mContext, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE))
                .build();

        Parcel p = Parcel.obtain();
        card.writeToParcel(p, 0);
        p.setDataPosition(0);
        WalletCard newCard = WalletCard.CREATOR.createFromParcel(p);
        assertThat(card.getCardId()).isEqualTo(newCard.getCardId());
        assertThat(card.getCardType()).isEqualTo(newCard.getCardType());
        compareIcons(mContext, card.getCardImage(), newCard.getCardImage());
        assertThat(card.getContentDescription()).isEqualTo(newCard.getContentDescription());
        assertThat(card.getPendingIntent()).isEqualTo(newCard.getPendingIntent());
        compareIcons(mContext, card.getCardIcon(), card.getCardIcon());
        assertThat(card.getCardLabel()).isEqualTo(newCard.getCardLabel());
        compareIcons(mContext, card.getNonPaymentCardSecondaryImage(),
                newCard.getNonPaymentCardSecondaryImage());
    }

    @Test
    public void testBuilder_defaultsToUnknownCardType() {
        Bitmap bitmap = Bitmap.createBitmap(70, 44, Bitmap.Config.ARGB_8888);
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        WalletCard card =
                new WalletCard.Builder(
                                "cardId",
                                Icon.createWithBitmap(bitmap),
                                "content description",
                                PendingIntent.getActivity(
                                        mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                        .build();

        assertThat(card.getCardType()).isEqualTo(WalletCard.CARD_TYPE_UNKNOWN);
    }

    @Test
    public void testSetNonPaymentCardSecondaryImage_throwsException() {
        Bitmap bitmap = Bitmap.createBitmap(70, 44, Bitmap.Config.ARGB_8888);
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new WalletCard.Builder(
                                        "cardId",
                                        WalletCard.CARD_TYPE_PAYMENT,
                                        Icon.createWithBitmap(bitmap),
                                        "content description",
                                        PendingIntent.getActivity(
                                                mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                                .setNonPaymentCardSecondaryImage(Icon.createWithBitmap(bitmap)));
    }

    @Test
    public void testSetCardLocationsNull_throwsException() {
        Bitmap bitmap = Bitmap.createBitmap(70, 44, Bitmap.Config.ARGB_8888);
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        assertThrows(
                NullPointerException.class,
                () ->
                        new WalletCard.Builder(
                                        "cardId",
                                        WalletCard.CARD_TYPE_PAYMENT,
                                        Icon.createWithBitmap(bitmap),
                                        "content description",
                                        PendingIntent.getActivity(
                                                mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                                .setCardLocations(List.of(new Location("test"), null)));
    }
}
