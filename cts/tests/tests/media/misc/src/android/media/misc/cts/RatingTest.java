/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.misc.cts;

import static android.media.Rating.RATING_3_STARS;
import static android.media.Rating.RATING_4_STARS;
import static android.media.Rating.RATING_5_STARS;
import static android.media.Rating.RATING_HEART;
import static android.media.Rating.RATING_NONE;
import static android.media.Rating.RATING_PERCENTAGE;
import static android.media.Rating.RATING_THUMB_UP_DOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.Rating;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link android.media.Rating}.
 *
 * TODO: Tests for applying invalid method (e.g. heartRating.getPercentRating()).
 * TODO: Tests for methods inherited from Parcelable
 */
@NonMainlineTest
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RatingTest {

    @Test
    public void testNewUnratedRating() {
        final int[] ratingStyles = new int[] { RATING_HEART, RATING_THUMB_UP_DOWN, RATING_3_STARS,
                RATING_4_STARS, RATING_5_STARS, RATING_PERCENTAGE };
        for (int ratingStyle : ratingStyles) {
            Rating rating = Rating.newUnratedRating(ratingStyle);
            assertNotNull(rating);
            assertEquals(ratingStyle, rating.getRatingStyle());
            assertFalse(rating.isRated());
        }

        final int[] invalidRatingStyles = new int[] {RATING_NONE, -1};
        for (int invalidRatingStyle : invalidRatingStyles) {
            Rating rating = Rating.newUnratedRating(invalidRatingStyle);
            assertNull(rating);
        }
    }

    @Test
    public void testHeartRating() {
        Rating ratingWithHeart = Rating.newHeartRating(/*hasHeart=*/ true);
        assertEquals(RATING_HEART, ratingWithHeart.getRatingStyle());
        assertTrue(ratingWithHeart.hasHeart());
        assertTrue(ratingWithHeart.isRated());

        Rating ratingWithoutHeart = Rating.newHeartRating(/*hasHeart=*/ false);
        assertEquals(RATING_HEART, ratingWithoutHeart.getRatingStyle());
        assertFalse(ratingWithoutHeart.hasHeart());
        assertTrue(ratingWithoutHeart.isRated());
    }

    @Test
    public void testHeartRatingWithIllegalRatingValueGetters() {
        Rating ratingWithHeart = Rating.newHeartRating(/*hasHeart=*/ true);
        assertFalse(ratingWithHeart.isThumbUp());
        assertTrue(ratingWithHeart.getStarRating() < 0f);
        assertTrue(ratingWithHeart.getPercentRating() < 0f);
    }

    @Test
    public void testThumbRating() {
        Rating ratingThumbUp = Rating.newThumbRating(/*thumbIsUp=*/ true);
        assertEquals(RATING_THUMB_UP_DOWN, ratingThumbUp.getRatingStyle());
        assertTrue(ratingThumbUp.isThumbUp());
        assertTrue(ratingThumbUp.isRated());

        Rating ratingThumbDown = Rating.newThumbRating(/*thumbIsUp=*/ false);
        assertEquals(RATING_THUMB_UP_DOWN, ratingThumbDown.getRatingStyle());
        assertFalse(ratingThumbDown.isThumbUp());
        assertTrue(ratingThumbDown.isRated());
    }

    @Test
    public void testThumbRatingWithIllegalRatingValueGetters() {
        Rating ratingThumbUp = Rating.newThumbRating(/*thumbIsUp=*/ true);
        assertFalse(ratingThumbUp.hasHeart());
        assertTrue(ratingThumbUp.getStarRating() < 0f);
        assertTrue(ratingThumbUp.getPercentRating() < 0f);
    }

    @Test
    public void testNewStarRatingWithInvalidStylesReturnsNull() {
        final int[] nonStarRatingStyles = new int[] { RATING_HEART, RATING_THUMB_UP_DOWN,
                RATING_PERCENTAGE, RATING_NONE };
        for (int nonStarRatingStyle : nonStarRatingStyles) {
            assertNull(Rating.newStarRating(nonStarRatingStyle, 1.0f));
        }
    }

    @Test
    public void testNewStarRatingWithInvalidRatingValuesReturnsNull() {
        assertNull(Rating.newStarRating(RATING_3_STARS, -1.0f));
        assertNull(Rating.newStarRating(RATING_3_STARS, 4f));
        assertNull(Rating.newStarRating(RATING_3_STARS, Float.MAX_VALUE));
        assertNull(Rating.newStarRating(RATING_3_STARS, Float.NaN));

        assertNull(Rating.newStarRating(RATING_4_STARS, -1.0f));
        assertNull(Rating.newStarRating(RATING_4_STARS, 5f));
        assertNull(Rating.newStarRating(RATING_4_STARS, Float.MAX_VALUE));
        assertNull(Rating.newStarRating(RATING_4_STARS, Float.NaN));

        assertNull(Rating.newStarRating(RATING_5_STARS, -1.0f));
        assertNull(Rating.newStarRating(RATING_5_STARS, 6f));
        assertNull(Rating.newStarRating(RATING_5_STARS, Float.MAX_VALUE));
        assertNull(Rating.newStarRating(RATING_5_STARS, Float.NaN));
    }

    @Test
    public void testStarRating() {
        final float starRatingValue = 1.5f;
        final int[] starRatingStyles = new int[] { RATING_3_STARS, RATING_4_STARS, RATING_5_STARS};

        for (int starRatingStyle : starRatingStyles) {
            Rating starRating = Rating.newStarRating(starRatingStyle, starRatingValue);
            assertNotNull(starRating);
            assertEquals(starRatingStyle, starRating.getRatingStyle());
            assertEquals(starRatingValue, starRating.getStarRating(), /*delta=*/ 0f);
            assertTrue(starRating.isRated());
        }
    }

    @Test
    public void testStarRatingWithIllegalRatingValueGetters() {
        Rating starRating = Rating.newStarRating(RATING_3_STARS, /*starValue=*/ 2.5f);
        assertFalse(starRating.hasHeart());
        assertFalse(starRating.isThumbUp());
        assertTrue(starRating.getPercentRating() < 0f);
    }

    @Test
    public void testNewPercentageRatingWithInvalidPercentValuesReturnsNull() {
        final float[] invalidPercentValues = new float[] {-1.0f, 100.1f, 200f, 1000f,
                Float.MAX_VALUE, Float.NaN};
        for (float invalidPercentValue : invalidPercentValues) {
            assertNull(Rating.newPercentageRating(invalidPercentValue));
        }
    }

    @Test
    public void testPercentageRating() {
        final float[] percentValues = new float[] { 0.0f, 20.0f, 33.3f, 50.0f, 64.5f, 89.9f, 100f};
        for (float percentValue : percentValues) {
            Rating percentageRating = Rating.newPercentageRating(percentValue);
            assertNotNull(percentageRating);
            assertEquals(RATING_PERCENTAGE, percentageRating.getRatingStyle());
            assertEquals(percentValue, percentageRating.getPercentRating(), /*delta=*/ 0f);
            assertTrue(percentageRating.isRated());
        }
    }

    @Test
    public void testPercentageWithIllegalRatingValueGetters() {
        Rating percentageRating = Rating.newPercentageRating(72.5f);
        assertFalse(percentageRating.hasHeart());
        assertFalse(percentageRating.isThumbUp());
        assertTrue(percentageRating.getStarRating() < 0f);
    }

    @SuppressWarnings("ReturnValueIgnored")
    @Test
    public void testToStringDoesNotCrash() {
        Rating rating = Rating.newHeartRating(/*hasHeart=*/ true);
        rating.toString(); // This should not crash.
    }

    @Test
    public void testParcelization() {
        Parcel p = Parcel.obtain();
        try {
            Rating rating = Rating.newStarRating(RATING_4_STARS, 3.5f);
            p.writeParcelable(rating, /*flags=*/ 0);
            p.setDataPosition(0);

            Rating ratingFromParcel = p.readParcelable(null);
            assertNotNull(ratingFromParcel);
            // TODO: Compare two rating using equals() when it is implemented.
            assertEquals(rating.getRatingStyle(), ratingFromParcel.getRatingStyle());
            assertEquals(rating.getStarRating(), ratingFromParcel.getStarRating(), 0f);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testCreatorNewArray() {
        final int arrayLength = 5;
        Rating[] ratingArrayInitializedWithNulls = Rating.CREATOR.newArray(arrayLength);
        assertNotNull(ratingArrayInitializedWithNulls);
        assertEquals(arrayLength, ratingArrayInitializedWithNulls.length);
        for (Rating rating : ratingArrayInitializedWithNulls) {
            assertNull(rating);
        }
    }
}
