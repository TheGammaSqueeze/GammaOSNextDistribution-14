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

package com.android.bluetooth.avrcp;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.audio_util.Image;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class AvrcpCoverArtStorageTest {
    private Resources mTestResources;

    private AvrcpCoverArtStorage mAvrcpCoverArtStorage;

    @Before
    public void setUp() throws Exception {
        mTestResources = TestUtils.getTestApplicationResources(
                InstrumentationRegistry.getTargetContext());

        mAvrcpCoverArtStorage = new AvrcpCoverArtStorage(2);
    }

    @After
    public void tearDown() throws Exception {
        mAvrcpCoverArtStorage.clear();
        mAvrcpCoverArtStorage = null;
        mTestResources = null;
    }

    private CoverArt getCoverArt(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        Bitmap bitmap = BitmapFactory.decodeStream(imageInputStream);
        Image image = new Image(null, bitmap);
        return new CoverArt(image);
    }

    /**
     * Make sure you can store and get an image handle for an image
     */
    @Test
    public void testStoreImage() {
        CoverArt artwork = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);
        String handle = mAvrcpCoverArtStorage.storeImage(artwork);
        assertThat(handle).isNotNull();
        assertThat(mAvrcpCoverArtStorage.getImage(handle)).isEqualTo(artwork);
    }

    /**
     * Make sure an attempt to store an image that is already stored yields the previous handle
     */
    @Test
    public void testStoreImageThatIsAlreadyStored() {
        CoverArt artwork = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);
        String handle = mAvrcpCoverArtStorage.storeImage(artwork);
        assertThat(handle).isNotNull();
        assertThat(mAvrcpCoverArtStorage.storeImage(artwork)).isEqualTo(handle);
    }

    /**
     * Make sure you can store and get an image handle for a second image thats not yet stored
     */
    @Test
    public void testStoreSecondImage() {
        CoverArt artwork_green = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);
        CoverArt artwork_blue = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200_blue);
        String handle_green = mAvrcpCoverArtStorage.storeImage(artwork_green);
        String handle_blue = mAvrcpCoverArtStorage.storeImage(artwork_blue);
        assertThat(handle_green).isNotNull();
        assertThat(handle_blue).isNotNull();
        assertThat(handle_green).isNotEqualTo(handle_blue);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_green)).isEqualTo(artwork_green);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_blue)).isEqualTo(artwork_blue);
    }

    /**
     * Make sure you can store and get an image handle for a third image thats not yet stored.
     *
     * Since the cache size is set to 2 for these tests, this third image should force the least
     * recently used image to be removed. This test has the LRU image as the first one entered.
     */
    @Test
    public void testStoreThirdImageWithLruAsFirstImage() {
        CoverArt artwork_green = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);
        CoverArt artwork_blue = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200_blue);
        CoverArt artwork_orange =
                getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200_orange);

        // Store image 1, 2 and 3, evicting image 1 when image 3 is stored
        String handle_green = mAvrcpCoverArtStorage.storeImage(artwork_green);
        String handle_blue = mAvrcpCoverArtStorage.storeImage(artwork_blue);
        String handle_orange = mAvrcpCoverArtStorage.storeImage(artwork_orange);

        // Check to make sure handles make sense
        assertThat(handle_green).isNotNull();
        assertThat(handle_blue).isNotNull();
        assertThat(handle_orange).isNotNull();
        assertThat(handle_green).isNotEqualTo(handle_blue);
        assertThat(handle_green).isNotEqualTo(handle_orange);
        assertThat(handle_blue).isNotEqualTo(handle_orange);

        // Make sure images 2 and 3 are available and image 1 is not
        assertThat(mAvrcpCoverArtStorage.getImage(handle_orange)).isEqualTo(artwork_orange);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_blue)).isEqualTo(artwork_blue);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_green)).isNull();
    }

    /**
     * Make sure you can store and get an image handle for a third image thats not yet stored.
     *
     * Since the cache size is set to 2 for these tests, this third image should force the least
     * recently used image to be removed. This test has the LRU image as the second one entered.
     */
    @Test
    public void testStoreThirdImageWithLruAsSecondImage() {
        CoverArt artwork_green = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);
        CoverArt artwork_blue = getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200_blue);
        CoverArt artwork_orange =
                getCoverArt(com.android.bluetooth.tests.R.raw.image_200_200_orange);

        // Store images 1 and 2, touch image 1 by getting it
        String handle_green = mAvrcpCoverArtStorage.storeImage(artwork_green);
        String handle_blue = mAvrcpCoverArtStorage.storeImage(artwork_blue);
        mAvrcpCoverArtStorage.getImage(handle_green);

        // Store image 3, evicting image 2
        String handle_orange = mAvrcpCoverArtStorage.storeImage(artwork_orange);

        // Check that handles make sense
        assertThat(handle_green).isNotNull();
        assertThat(handle_blue).isNotNull();
        assertThat(handle_orange).isNotNull();
        assertThat(handle_green).isNotEqualTo(handle_blue);
        assertThat(handle_green).isNotEqualTo(handle_orange);
        assertThat(handle_blue).isNotEqualTo(handle_orange);

        // Make sure image 1 and image 3 are available readily, image 2 is evicted
        assertThat(mAvrcpCoverArtStorage.getImage(handle_orange)).isEqualTo(artwork_orange);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_green)).isEqualTo(artwork_green);
        assertThat(mAvrcpCoverArtStorage.getImage(handle_blue)).isNull();
    }
}
