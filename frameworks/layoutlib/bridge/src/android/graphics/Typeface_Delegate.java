/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ide.common.rendering.api.AndroidConstants;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.FontResourcesParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Delegate implementing the native methods of android.graphics.Typeface
 * <p>
 * Through the layoutlib_create tool, the original native methods of Typeface have been replaced by
 * calls to methods of the same name in this delegate class.
 * <p>
 * This class behaves like the original native implementation, but in Java, keeping previously
 * native data into its own objects and mapping them to int that are sent back and forth between it
 * and the original Typeface class.
 *
 * @see DelegateManager
 */
public final class Typeface_Delegate {
    /**
     * Loads a single font or font family from disk
     */
    @Nullable
    public static Typeface createFromDisk(@NonNull BridgeContext context, @NonNull String path,
            boolean isFramework) {
        // Check if this is an asset that we've already loaded dynamically
        Typeface typeface = Typeface.findFromCache(context.getAssets(), path);
        if (typeface != null) {
            return typeface;
        }

        if (path.isBlank()) {
            return null;
        }

        String lowerCaseValue = path.toLowerCase();
        if (lowerCaseValue.endsWith(AndroidConstants.DOT_XML)) {
            // create a block parser for the file
            XmlPullParser parser = context.getLayoutlibCallback().createXmlParserForPsiFile(path);

            if (parser != null) {
                // TODO(b/156609434): The aapt namespace should not matter for parsing font files?
                BridgeXmlBlockParser blockParser =
                        new BridgeXmlBlockParser(
                                parser, context, ResourceNamespace.fromBoolean(isFramework));
                try {
                    FontResourcesParser.FamilyResourceEntry entry =
                            FontResourcesParser.parse(blockParser, context.getResources());
                    typeface = Typeface.createFromResources(entry, context.getAssets(), path);
                } catch (XmlPullParserException | IOException e) {
                    Bridge.getLog().error(null, "Failed to parse file " + path, e, null,
                            null /*data*/);
                } finally {
                    blockParser.ensurePopped();
                }
            } else {
                Bridge.getLog().error(ILayoutLog.TAG_BROKEN,
                        String.format("File %s does not exist (or is not a file)", path),
                        null, null /*data*/);
            }
        } else {
            typeface = new Typeface.Builder(context.getAssets(), path, false, 0).build();
        }

        return typeface;
    }

    @LayoutlibDelegate
    /*package*/ static Typeface create(String familyName, int style) {
        if (familyName != null && Files.exists(Paths.get(familyName))) {
            // Workaround for b/64137851
            // Support lib will call this method after failing to create the TypefaceCompat.
            return Typeface_Delegate.createFromDisk(RenderAction.getCurrentContext(), familyName,
                    false);
        }
        return Typeface.create_Original(familyName, style);
    }

    @LayoutlibDelegate
    /*package*/ static Typeface create(Typeface family, int style) {
        return Typeface.create_Original(family, style);
    }

    @LayoutlibDelegate
    /*package*/ static Typeface create(Typeface family, int style, boolean isItalic) {
        return Typeface.create_Original(family, style, isItalic);
    }
}
