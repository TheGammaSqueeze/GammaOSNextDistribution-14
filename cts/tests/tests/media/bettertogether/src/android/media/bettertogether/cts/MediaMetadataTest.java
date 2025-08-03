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
package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Tests {@link MediaMetadata}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@NonMainlineTest
public class MediaMetadataTest {

    @Test
    public void builder_defaultConstructor_hasNoData() {
        MediaMetadata metadata = new MediaMetadata.Builder().build();

        assertThat(metadata.size()).isEqualTo(0);
        assertThat(metadata.keySet()).isEmpty();
    }

    @Test
    public void builder_putText() {
        String testTitle = "test_title";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putText(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .build();

        assertThat(metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE)).isTrue();
        CharSequence titleOut = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        assertThat(titleOut).isEqualTo(testTitle);
    }

    @Test
    public void builder_putString() {
        String testTitle = "test_title";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .build();

        assertThat(metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE)).isTrue();
        String titleOut = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        assertThat(titleOut).isEqualTo(testTitle);
    }

    @Test
    public void builder_putLong() {
        long testYear = 2021;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertThat(metadata.containsKey(MediaMetadata.METADATA_KEY_YEAR)).isTrue();
        long yearOut = metadata.getLong(MediaMetadata.METADATA_KEY_YEAR);
        assertThat(yearOut).isEqualTo(testYear);
    }

    @Test
    public void builder_putRating() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .build();

        assertThat(metadata.containsKey(MediaMetadata.METADATA_KEY_RATING)).isTrue();
        Rating ratingOut = metadata.getRating(MediaMetadata.METADATA_KEY_RATING);
        assertThat(ratingOut).isEqualTo(testHeartRating);
    }

    @Test
    public void builder_putText_throwsIAE_withNonTextKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.putText(MediaMetadata.METADATA_KEY_YEAR, "test"));
    }

    @Test
    public void builder_putString_throwsIAE_withNonTextKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.putString(MediaMetadata.METADATA_KEY_YEAR, "test"));
    }

    @Test
    public void builder_putLong_throwsIAE_withNonLongKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.putLong(MediaMetadata.METADATA_KEY_TITLE, 2021));
    }

    @Test
    public void builder_putRating_throwsIAE_withNonRatingKey() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.putRating(MediaMetadata.METADATA_KEY_TITLE, testHeartRating));
    }

    @Test
    public void builder_putBitmap_throwsIAE_withNonBitmapKey() {
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.putBitmap(MediaMetadata.METADATA_KEY_TITLE, testBitmap));
    }

    @Test
    public void builder_copyConstructor() {
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        MediaMetadata copiedMetadata = new MediaMetadata.Builder(originalMetadata).build();
        assertThat(copiedMetadata).isEqualTo(originalMetadata);
    }

    @Test
    public void equalsAndHashCode() {
        String testTitle = "test_title";
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();
        MediaMetadata metadataToCompare = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertThat(metadataToCompare).isEqualTo(originalMetadata);
        assertThat(metadataToCompare.hashCode()).isEqualTo(originalMetadata.hashCode());
    }

    @Test
    public void equalsAndHashCode_ignoreRatingAndBitmap() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, testBitmap)
                .build();
        MediaMetadata emptyMetadata = new MediaMetadata.Builder().build();

        assertThat(emptyMetadata).isEqualTo(originalMetadata);
        assertThat(emptyMetadata.hashCode()).isEqualTo(originalMetadata.hashCode());
    }

    @Test
    public void sizeAndKeySet() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, testBitmap)
                .build();

        assertThat(metadata.size()).isEqualTo(2);
        Set<String> keySet = metadata.keySet();
        assertThat(keySet).containsExactly(
                MediaMetadata.METADATA_KEY_RATING, MediaMetadata.METADATA_KEY_ART);
    }

    @Test
    public void describeContents() {
        long testYear = 2021;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertThat(metadata.describeContents()).isEqualTo(0);
    }

    @Test
    public void writeToParcel() {
        String testTitle = "test_title";
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        Parcel parcel = Parcel.obtain();
        originalMetadata.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        MediaMetadata metadataOut = MediaMetadata.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(metadataOut).isEqualTo(originalMetadata);
    }

    @Test
    public void getDescription() {
        String testMediaId = "media_id";
        String testTitle = "test_title";
        String testSubtitle = "test_subtitle";
        String testDescription = "test_description";
        Bitmap testIcon = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        String testMediaUri = "https://www.google.com";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, testMediaId)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, testTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, testSubtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, testDescription)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testIcon)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, testMediaUri)
                .build();

        MediaDescription mediaDescription = metadata.getDescription();
        assertThat(mediaDescription.getMediaId()).isEqualTo(testMediaId);
        assertThat(mediaDescription.getTitle()).isEqualTo(testTitle);
        assertThat(mediaDescription.getSubtitle()).isEqualTo(testSubtitle);
        assertThat(mediaDescription.getDescription()).isEqualTo(testDescription);
        assertThat(mediaDescription.getIconBitmap()).isNotNull();
        assertThat(
                TextUtils.equals(testMediaUri, mediaDescription.getMediaUri().toString())).isTrue();
    }

    @Test
    public void getBitmapDimensionLimit_returnsIntegerMaxWhenNotSet() {
        MediaMetadata metadata = new MediaMetadata.Builder().build();
        assertThat(metadata.getBitmapDimensionLimit()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void builder_setBitmapDimensionLimit_bitmapsAreScaledDown() {
        // A large bitmap (64MB).
        final int originalWidth = 4096;
        final int originalHeight = 4096;
        Bitmap testBitmap = Bitmap.createBitmap(
                originalWidth, originalHeight, Bitmap.Config.ARGB_8888);

        final int testBitmapDimensionLimit = 16;

        MediaMetadata metadata = new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testBitmap)
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertThat(metadata.getBitmapDimensionLimit()).isEqualTo(testBitmapDimensionLimit);

        Bitmap scaledDownBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        assertThat(scaledDownBitmap).isNotNull();
        assertThat(scaledDownBitmap.getWidth() <= testBitmapDimensionLimit).isTrue();
        assertThat(scaledDownBitmap.getHeight() <= testBitmapDimensionLimit).isTrue();
    }

    @Test
    public void builder_setBitmapDimensionLimit_bitmapsAreNotScaledDown() {
        // A small bitmap.
        final int originalWidth = 16;
        final int originalHeight = 16;
        Bitmap testBitmap = Bitmap.createBitmap(
                originalWidth, originalHeight, Bitmap.Config.ARGB_8888);

        // The limit is larger than the width/height.
        final int testBitmapDimensionLimit = 256;

        MediaMetadata metadata = new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testBitmap)
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertThat(metadata.getBitmapDimensionLimit()).isEqualTo(testBitmapDimensionLimit);

        Bitmap notScaledDownBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        assertThat(notScaledDownBitmap).isNotNull();
        assertThat(notScaledDownBitmap.getWidth()).isEqualTo(originalWidth);
        assertThat(notScaledDownBitmap.getHeight()).isEqualTo(originalHeight);
    }

    @Test
    public void builder_setMaxBitmapDimensionLimit_unsetLimit() {
        final int testBitmapDimensionLimit = 256;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertThat(metadata.getBitmapDimensionLimit()).isEqualTo(testBitmapDimensionLimit);

        // Using copy constructor, unset the limit by passing Integer.MAX_VALUE to the limit.
        MediaMetadata copiedMetadataWithLimitUnset = new MediaMetadata.Builder()
                .setBitmapDimensionLimit(Integer.MAX_VALUE)
                .build();
        assertThat(copiedMetadataWithLimitUnset.getBitmapDimensionLimit())
                .isEqualTo(Integer.MAX_VALUE);
    }

}
