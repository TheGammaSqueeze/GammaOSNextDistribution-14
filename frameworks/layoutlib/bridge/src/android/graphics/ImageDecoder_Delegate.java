/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.graphics;

import com.android.layoutlib.bridge.util.NinePatchInputStream;
import com.android.ninepatch.GraphicsUtilities;
import com.android.ninepatch.NinePatch;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.annotation.NonNull;
import android.graphics.Bitmap.Config;
import android.graphics.ImageDecoder.InputStreamSource;
import android.graphics.ImageDecoder.OnHeaderDecodedListener;
import android.graphics.ImageDecoder.ResourceSource;
import android.graphics.ImageDecoder.Source;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageDecoder_Delegate {

    @LayoutlibDelegate
    /*package*/ static Bitmap decodeBitmapImpl(@NonNull Source src,
            @NonNull OnHeaderDecodedListener listener) throws IOException {
        if (src instanceof ResourceSource) {
            // Bypass ImageDecoder for ResourceSource as it goes through the native AssetManager
            // which is not supported in layoutlib.
            ResourceSource source = (ResourceSource) src;
            return BitmapFactory.decodeResource(source.mResources, source.mResId);
        }
        InputStream stream = src instanceof InputStreamSource ?
                ((InputStreamSource) src).mInputStream : null;
        Bitmap bm = ImageDecoder.decodeBitmapImpl_Original(src, listener);
        if (stream instanceof NinePatchInputStream && bm.getNinePatchChunk() == null) {
            stream = new FileInputStream(((NinePatchInputStream) stream).getPath());
            NinePatch ninePatch = NinePatch.load(stream, true /*is9Patch*/, false /* convert */);
            BufferedImage image = ninePatch.getImage();

            // width and height of the nine patch without the special border.
            int width = image.getWidth();
            int height = image.getHeight();

            // Get pixel data from image independently of its type.
            int[] imageData = GraphicsUtilities.getPixels(image, 0, 0, width, height, null);

            bm = Bitmap.createBitmap(imageData, width, height, Config.ARGB_8888);

            bm.setDensity(src.getDensity());
            bm.setNinePatchChunk(ninePatch.getChunk().getSerializedChunk());
        }
        return bm;
    }
}
