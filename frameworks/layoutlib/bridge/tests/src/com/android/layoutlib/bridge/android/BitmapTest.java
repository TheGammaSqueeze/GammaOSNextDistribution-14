/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.layoutlib.bridge.android;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.layoutlib.bridge.impl.RenderActionTestUtil;
import com.android.layoutlib.bridge.intensive.LayoutLibTestCallback;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.ninepatch.NinePatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import android.R;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.ImageDecoder.Source;
import android.util.DisplayMetrics;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertNotNull;

public class BitmapTest extends RenderTestBase {
    @BeforeClass
    public static void setUp() {
        Bridge.prepareThread();
    }

    @Test
    public void testNinePatchChunk() throws IOException {
        InputStream compiled =
                getClass().getResourceAsStream("/com/android/layoutlib/testdata/compiled.9.png");
        Bitmap compiledBitmap = BitmapFactory.decodeStream(compiled, null, null);

        InputStream nonCompiled = getClass().getResourceAsStream(
                "/com/android/layoutlib/testdata/non_compiled.9.png");
        NinePatch ninePatch = NinePatch.load(nonCompiled, true, false);

        Assert.assertArrayEquals(compiledBitmap.getNinePatchChunk(), ninePatch.getChunk().getSerializedChunk());
    }

//    @Test
//    public void testNativeBitmap() {
//        InputStream compiled =
//                getClass().getResourceAsStream("/com/android/layoutlib/testdata/compiled.9.png");
//        Bitmap compiledBitmap = BitmapFactory.decodeStream(compiled, null, null);
//        assertNotNull(compiledBitmap);
//        Buffer buffer = ByteBuffer.allocate(compiledBitmap.getByteCount());
//        compiledBitmap.copyPixelsToBuffer(buffer);
//        buffer.rewind();
//        compiledBitmap.copyPixelsFromBuffer(buffer);
//    }

    @Test
    public void testImageDecoder() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());

        context.initResources(params.getAssets());
        BridgeContext oldContext = RenderActionTestUtil.setBridgeContext(context);
        try {
            Source source = ImageDecoder.createSource(context.getResources(),
                    R.drawable.button_onoff_indicator_on);
            Bitmap bitmap = ImageDecoder.decodeBitmap(source);
            BufferedImage image = new BufferedImage(bitmap.getWidth(), bitmap.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            int[] imageData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            bitmap.getPixels(imageData, 0, image.getWidth(), 0, 0, image.getWidth(),
                    image.getHeight());
            verify("bitmap_decoder.png", image);
        } finally {
            RenderActionTestUtil.setBridgeContext(oldContext);
            context.disposeResources();
        }
    }
}
