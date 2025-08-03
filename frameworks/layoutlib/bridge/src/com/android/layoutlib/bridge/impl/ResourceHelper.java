/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.layoutlib.bridge.impl;

import com.android.ide.common.rendering.api.AndroidConstants;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.internal.util.XmlUtils;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeContext.Key;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.ninepatch.GraphicsUtilities;
import com.android.ninepatch.NinePatch;
import com.android.resources.Density;
import com.android.resources.ResourceType;

import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.BridgeAssetManager;
import android.content.res.ColorStateList;
import android.content.res.ComplexColor;
import android.content.res.ComplexColor_Accessor;
import android.content.res.GradientColor;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.StringBlock;
import android.content.res.StringBlock.Height;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Typeface_Accessor;
import android.graphics.Typeface_Delegate;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

import static android.content.res.AssetManager.ACCESS_STREAMING;

/**
 * Helper class to provide various conversion method used in handling android resources.
 */
public final class ResourceHelper {
    private static final Key<Set<ResourceValue>> KEY_GET_DRAWABLE =
            Key.create("ResourceHelper.getDrawable");
    private static final Pattern sFloatPattern = Pattern.compile("(-?[0-9]*(?:\\.[0-9]*)?)(.*)");
    private static final float[] sFloatOut = new float[1];

    private static final TypedValue mValue = new TypedValue();

    /**
     * Returns the color value represented by the given string value.
     *
     * @param value the color value
     * @return the color as an int
     * @throws NumberFormatException if the conversion failed.
     */
    public static int getColor(@Nullable String value) {
        if (value == null) {
            throw new NumberFormatException("null value");
        }

        value = value.trim();
        int len = value.length();

        // make sure it's not longer than 32bit or smaller than the RGB format
        if (len < 2 || len > 9) {
            throw new NumberFormatException(String.format(
                    "Color value '%s' has wrong size. Format is either" +
                            "#AARRGGBB, #RRGGBB, #RGB, or #ARGB",
                    value));
        }

        if (value.charAt(0) != '#') {
            if (value.startsWith(AndroidConstants.PREFIX_THEME_REF)) {
                throw new NumberFormatException(String.format(
                        "Attribute '%s' not found. Are you using the right theme?", value));
            }
            throw new NumberFormatException(
                    String.format("Color value '%s' must start with #", value));
        }

        value = value.substring(1);

        if (len == 4) { // RGB format
            char[] color = new char[8];
            color[0] = color[1] = 'F';
            color[2] = color[3] = value.charAt(0);
            color[4] = color[5] = value.charAt(1);
            color[6] = color[7] = value.charAt(2);
            value = new String(color);
        } else if (len == 5) { // ARGB format
            char[] color = new char[8];
            color[0] = color[1] = value.charAt(0);
            color[2] = color[3] = value.charAt(1);
            color[4] = color[5] = value.charAt(2);
            color[6] = color[7] = value.charAt(3);
            value = new String(color);
        } else if (len == 7) {
            value = "FF" + value;
        }

        // this is a RRGGBB or AARRGGBB value

        // Integer.parseInt will fail to parse strings like "ff191919", so we use
        // a Long, but cast the result back into an int, since we know that we're only
        // dealing with 32 bit values.
        return (int)Long.parseLong(value, 16);
    }

    /**
     * Returns a {@link ComplexColor} from the given {@link ResourceValue}
     *
     * @param resValue the value containing a color value or a file path to a complex color
     * definition
     * @param context the current context
     * @param theme the theme to use when resolving the complex color
     * @param allowGradients when false, only {@link ColorStateList} will be returned. If a {@link
     * GradientColor} is found, null will be returned.
     */
    @Nullable
    private static ComplexColor getInternalComplexColor(@NonNull ResourceValue resValue,
            @NonNull BridgeContext context, @Nullable Theme theme, boolean allowGradients) {
        String value = resValue.getValue();
        if (value == null || RenderResources.REFERENCE_NULL.equals(value)) {
            return null;
        }

        // try to load the color state list from an int
        if (value.trim().startsWith("#")) {
            try {
                int color = getColor(value);
                return ColorStateList.valueOf(color);
            } catch (NumberFormatException e) {
                Bridge.getLog().warning(ILayoutLog.TAG_RESOURCES_FORMAT,
                        String.format("\"%1$s\" cannot be interpreted as a color.", value),
                        null, null);
                return null;
            }
        }

        try {
            BridgeXmlBlockParser blockParser = getXmlBlockParser(context, resValue);
            if (blockParser != null) {
                try {
                    // Advance the parser to the first element so we can detect if it's a
                    // color list or a gradient color
                    int type;
                    //noinspection StatementWithEmptyBody
                    while ((type = blockParser.next()) != XmlPullParser.START_TAG
                            && type != XmlPullParser.END_DOCUMENT) {
                        // Seek parser to start tag.
                    }

                    if (type != XmlPullParser.START_TAG) {
                        assert false : "No start tag found";
                        return null;
                    }

                    final String name = blockParser.getName();
                    if (allowGradients && "gradient".equals(name)) {
                        return ComplexColor_Accessor.createGradientColorFromXmlInner(
                                context.getResources(),
                                blockParser, blockParser,
                                theme);
                    } else if ("selector".equals(name)) {
                        return ComplexColor_Accessor.createColorStateListFromXmlInner(
                                context.getResources(),
                                blockParser, blockParser,
                                theme);
                    }
                } finally {
                    blockParser.ensurePopped();
                }
            }
        } catch (XmlPullParserException e) {
            Bridge.getLog().error(ILayoutLog.TAG_BROKEN,
                    "Failed to configure parser for " + value, e, null,null /*data*/);
            // we'll return null below.
        } catch (Exception e) {
            // this is an error and not warning since the file existence is
            // checked before attempting to parse it.
            Bridge.getLog().error(ILayoutLog.TAG_RESOURCES_READ,
                    "Failed to parse file " + value, e, null, null /*data*/);

            return null;
        }

        return null;
    }

    /**
     * Returns a {@link ColorStateList} from the given {@link ResourceValue}
     *
     * @param resValue the value containing a color value or a file path to a complex color
     * definition
     * @param context the current context
     */
    @Nullable
    public static ColorStateList getColorStateList(@NonNull ResourceValue resValue,
            @NonNull BridgeContext context, @Nullable Resources.Theme theme) {
        return (ColorStateList) getInternalComplexColor(resValue, context,
                theme != null ? theme : context.getTheme(),
                false);
    }

    /**
     * Returns a {@link ComplexColor} from the given {@link ResourceValue}
     *
     * @param resValue the value containing a color value or a file path to a complex color
     * definition
     * @param context the current context
     */
    @Nullable
    public static ComplexColor getComplexColor(@NonNull ResourceValue resValue,
            @NonNull BridgeContext context, @Nullable Resources.Theme theme) {
        return getInternalComplexColor(resValue, context,
                theme != null ? theme : context.getTheme(),
                true);
    }

    /**
     * Returns a drawable from the given value.
     *
     * @param value The value that contains a path to a 9 patch, a bitmap or a xml based drawable,
     *     or an hexadecimal color
     * @param context the current context
     */
    @Nullable
    public static Drawable getDrawable(ResourceValue value, BridgeContext context) {
        return getDrawable(value, context, null);
    }

    /**
     * Returns a {@link BridgeXmlBlockParser} to parse the given {@link ResourceValue}. The passed
     * value must point to an XML resource.
     */
    @Nullable
    public static BridgeXmlBlockParser getXmlBlockParser(@NonNull BridgeContext context,
            @NonNull ResourceValue value) throws XmlPullParserException {
        String stringValue = value.getValue();
        if (RenderResources.REFERENCE_NULL.equals(stringValue)) {
            return null;
        }

        XmlPullParser parser = null;
        ResourceNamespace namespace;

        LayoutlibCallback layoutlibCallback = context.getLayoutlibCallback();
        // Framework values never need a PSI parser. They do not change and the do not contain
        // aapt:attr attributes.
        if (!value.isFramework()) {
            parser = layoutlibCallback.getParser(value);
        }

        if (parser != null) {
            namespace = ((ILayoutPullParser) parser).getLayoutNamespace();
        } else {
            parser = ParserFactory.create(stringValue);
            namespace = value.getNamespace();
        }

        return parser == null
                ? null
                : new BridgeXmlBlockParser(parser, context, namespace);
    }

    /**
     * Returns a drawable from the given value.
     *
     * @param value The value that contains a path to a 9 patch, a bitmap or a xml based drawable,
     *     or an hexadecimal color
     * @param context the current context
     * @param theme the theme to be used to inflate the drawable.
     */
    @Nullable
    public static Drawable getDrawable(ResourceValue value, BridgeContext context, Theme theme) {
        if (value == null) {
            return null;
        }
        String stringValue = value.getValue();
        if (RenderResources.REFERENCE_NULL.equals(stringValue)) {
            return null;
        }

        // try the simple case first. Attempt to get a color from the value
        if (stringValue.trim().startsWith("#")) {
            try {
                int color = getColor(stringValue);
                return new ColorDrawable(color);
            } catch (NumberFormatException e) {
                Bridge.getLog().warning(ILayoutLog.TAG_RESOURCES_FORMAT,
                        String.format("\"%1$s\" cannot be interpreted as a color.", stringValue),
                        null, null);
                return null;
            }
        }

        Density density = Density.MEDIUM;
        if (value instanceof DensityBasedResourceValue) {
            density = ((DensityBasedResourceValue) value).getResourceDensity();
            if (density == Density.NODPI || density == Density.ANYDPI) {
                density = Density.create(context.getConfiguration().densityDpi);
            }
        }

        String lowerCaseValue = stringValue.toLowerCase();
        if (lowerCaseValue.endsWith(".xml") || value.getResourceType() == ResourceType.AAPT) {
            // create a block parser for the file
            try {
                BridgeXmlBlockParser blockParser = getXmlBlockParser(context, value);
                if (blockParser != null) {
                    Set<ResourceValue> visitedValues = context.getUserData(KEY_GET_DRAWABLE);
                    if (visitedValues == null) {
                        visitedValues = new HashSet<>();
                        context.putUserData(KEY_GET_DRAWABLE, visitedValues);
                    }
                    if (!visitedValues.add(value)) {
                        Bridge.getLog().error(null, "Cyclic dependency in " + stringValue, null,
                                null);
                        return null;
                    }

                    try {
                        return Drawable.createFromXml(context.getResources(), blockParser, theme);
                    } finally {
                        visitedValues.remove(value);
                        blockParser.ensurePopped();
                    }
                }
            } catch (Exception e) {
                // this is an error and not warning since the file existence is checked before
                // attempting to parse it.
                Bridge.getLog().error(null, "Failed to parse file " + stringValue, e,
                        null, null /*data*/);
            }

            return null;
        } else {
            AssetRepository repository = getAssetRepository(context);
            if (repository.isFileResource(stringValue)) {
                try {
                    Bitmap bitmap = Bridge.getCachedBitmap(stringValue,
                            value.isFramework() ? null : context.getProjectKey());

                    if (bitmap == null) {
                        InputStream stream;
                        try {
                            stream = repository.openNonAsset(0, stringValue, ACCESS_STREAMING);

                        } catch (FileNotFoundException e) {
                            stream = null;
                        }
                        Options options = new Options();
                        options.inDensity = density.getDpiValue();
                        Rect padding = new Rect();
                        bitmap = BitmapFactory.decodeStream(stream, padding, options);
                        if (bitmap != null && bitmap.getNinePatchChunk() == null &&
                                lowerCaseValue.endsWith(NinePatch.EXTENSION_9PATCH)) {
                            //We are dealing with a non-compiled nine patch.
                            stream = repository.openNonAsset(0, stringValue, ACCESS_STREAMING);
                            NinePatch ninePatch = NinePatch.load(stream, true /*is9Patch*/, false /* convert */);
                            BufferedImage image = ninePatch.getImage();

                            // width and height of the nine patch without the special border.
                            int width = image.getWidth();
                            int height = image.getHeight();

                            // Get pixel data from image independently of its type.
                            int[] imageData = GraphicsUtilities.getPixels(image, 0, 0, width,
                                    height, null);

                            bitmap = Bitmap.createBitmap(imageData, width, height, Config.ARGB_8888);

                            bitmap.setDensity(options.inDensity);
                            bitmap.setNinePatchChunk(ninePatch.getChunk().getSerializedChunk());
                            int[] padArray = ninePatch.getChunk().getPadding();
                            padding.set(padArray[0], padArray[1], padArray[2], padArray[3]);
                        }
                        Bridge.setCachedBitmapPadding(stringValue, padding,
                                value.isFramework() ? null : context.getProjectKey());
                        Bridge.setCachedBitmap(stringValue, bitmap,
                                value.isFramework() ? null : context.getProjectKey());
                    }

                    if (bitmap != null && bitmap.getNinePatchChunk() != null) {
                        Rect padding = Bridge.getCachedBitmapPadding(stringValue,
                                value.isFramework() ? null : context.getProjectKey());
                        return new NinePatchDrawable(context.getResources(), bitmap, bitmap
                                .getNinePatchChunk(), padding, lowerCaseValue);
                    } else {
                        return new BitmapDrawable(context.getResources(), bitmap);
                    }
                } catch (IOException e) {
                    // we'll return null below
                    Bridge.getLog().error(ILayoutLog.TAG_RESOURCES_READ,
                            "Failed to load " + stringValue, e, null, null /*data*/);
                }
            }
        }

        return null;
    }

    private static AssetRepository getAssetRepository(@NonNull BridgeContext context) {
        BridgeAssetManager assetManager = context.getAssets();
        return assetManager.getAssetRepository();
    }

    /**
     * Returns a {@link Typeface} given a font name. The font name, can be a system font family
     * (like sans-serif) or a full path if the font is to be loaded from resources.
     */
    public static Typeface getFont(String fontName, BridgeContext context, Theme theme, boolean
            isFramework) {
        if (fontName == null || fontName.isBlank()) {
            return null;
        }

        if (Typeface_Accessor.isSystemFont(fontName)) {
            // Shortcut for the case where we are asking for a system font name. Those are not
            // loaded using external resources.
            return null;
        }


        return Typeface_Delegate.createFromDisk(context, fontName, isFramework);
    }

    /**
     * Returns a {@link Typeface} given a font name. The font name, can be a system font family
     * (like sans-serif) or a full path if the font is to be loaded from resources.
     */
    public static Typeface getFont(ResourceValue value, BridgeContext context, Theme theme) {
        if (value == null) {
            return null;
        }

        return getFont(value.getValue(), context, theme, value.isFramework());
    }

    /**
     * Looks for an attribute in the current theme.
     *
     * @param resources the render resources
     * @param attr the attribute reference
     * @param defaultValue the default value.
     * @return the value of the attribute or the default one if not found.
     */
    public static boolean getBooleanThemeValue(@NonNull RenderResources resources,
            @NonNull ResourceReference attr, boolean defaultValue) {
        ResourceValue value = resources.findItemInTheme(attr);
        value = resources.resolveResValue(value);
        if (value == null) {
            return defaultValue;
        }
        return XmlUtils.convertValueToBoolean(value.getValue(), defaultValue);
    }

    /**
     * Looks for a framework attribute in the current theme.
     *
     * @param resources the render resources
     * @param name the name of the attribute
     * @param defaultValue the default value.
     * @return the value of the attribute or the default one if not found.
     */
    public static boolean getBooleanThemeFrameworkAttrValue(@NonNull RenderResources resources,
            @NonNull String name, boolean defaultValue) {
        ResourceReference attrRef = BridgeContext.createFrameworkAttrReference(name);
        return getBooleanThemeValue(resources, attrRef, defaultValue);
    }

    /**
     * This takes a resource string containing HTML tags for styling,
     * and returns it correctly formatted to be displayed.
     */
    public static CharSequence parseHtml(String string) {
        // The parser requires <li> tags to be surrounded by <ul> tags to handle whitespace
        // correctly, though Android does not support <ul> tags.
        String str = string.replaceAll("<li>", "<ul><li>")
                .replaceAll("</li>","</li></ul>");
        int firstTagIndex = str.indexOf('<');
        if (firstTagIndex == -1) {
            return string;
        }
        int lastTagIndex = str.lastIndexOf('>');
        StringBuilder stringBuilder = new StringBuilder(str.substring(0, firstTagIndex));
        List<Tag> tagList = new ArrayList<>();
        Map<String, Deque<Tag>> startStacks = new HashMap<>();
        Parser parser = new Parser();
        parser.setContentHandler(new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                if (!Strings.isNullOrEmpty(localName)) {
                    Tag tag = new Tag(localName);
                    tag.mStart = stringBuilder.length();
                    tag.mAttributes = attributes;
                    startStacks.computeIfAbsent(localName, key -> new ArrayDeque<>()).addFirst(tag);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (!Strings.isNullOrEmpty(localName)) {
                    Tag tag = startStacks.get(localName).removeFirst();
                    tag.mEnd = stringBuilder.length();
                    tagList.add(tag);
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                stringBuilder.append(ch, start, length);
            }
        });
        try {
            parser.setProperty(Parser.schemaProperty, new HTMLSchema());
            parser.parse(new InputSource(
                    new StringReader(str.substring(firstTagIndex, lastTagIndex + 1))));
        } catch (SAXException | IOException e) {
            Bridge.getLog().warning(ILayoutLog.TAG_RESOURCES_FORMAT,
                    "The string " + str + " is not valid HTML", null, null);
            return str;
        }
        stringBuilder.append(str.substring(lastTagIndex + 1));
        return applyStyles(stringBuilder, tagList);
    }

    /**
     * This applies the styles from tagList that are supported by Android
     * and returns a {@link SpannedString}.
     * This should mirror {@link StringBlock#applyStyles}
     */
    @NonNull
    private static SpannedString applyStyles(@NonNull StringBuilder stringBuilder,
            @NonNull List<Tag> tagList) {
        SpannableString spannableString = new SpannableString(stringBuilder);
        for (Tag tag : tagList) {
            int start = tag.mStart;
            int end = tag.mEnd;
            Attributes attrs = tag.mAttributes;
            switch (tag.mLabel) {
                case "b":
                    spannableString.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "i":
                    spannableString.setSpan(new StyleSpan(Typeface.ITALIC), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "u":
                    spannableString.setSpan(new UnderlineSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "tt":
                    spannableString.setSpan(new TypefaceSpan("monospace"), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "big":
                    spannableString.setSpan(new RelativeSizeSpan(1.25f), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "small":
                    spannableString.setSpan(new RelativeSizeSpan(0.8f), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "sup":
                    spannableString.setSpan(new SuperscriptSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "sub":
                    spannableString.setSpan(new SubscriptSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "strike":
                    spannableString.setSpan(new StrikethroughSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "li":
                    StringBlock.addParagraphSpan(spannableString, new BulletSpan(10), start, end);
                    break;
                case "marquee":
                    spannableString.setSpan(TextUtils.TruncateAt.MARQUEE, start, end,
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    break;
                case "font":
                    String heightAttr = attrs.getValue("height");
                    if (heightAttr != null) {
                        int height = Integer.parseInt(heightAttr);
                        StringBlock.addParagraphSpan(spannableString, new Height(height), start,
                                end);
                    }

                    String sizeAttr = attrs.getValue("size");
                    if (sizeAttr != null) {
                        int size = Integer.parseInt(sizeAttr);
                        spannableString.setSpan(new AbsoluteSizeSpan(size, true), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    String fgcolorAttr = attrs.getValue("fgcolor");
                    if (fgcolorAttr != null) {
                        spannableString.setSpan(StringBlock.getColor(fgcolorAttr, true), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    String colorAttr = attrs.getValue("color");
                    if (colorAttr != null) {
                        spannableString.setSpan(StringBlock.getColor(colorAttr, true), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    String bgcolorAttr = attrs.getValue("bgcolor");
                    if (bgcolorAttr != null) {
                        spannableString.setSpan(StringBlock.getColor(bgcolorAttr, false), start,
                                end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    String faceAttr = attrs.getValue("face");
                    if (faceAttr != null) {
                        spannableString.setSpan(new TypefaceSpan(faceAttr), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    break;
                case "a":
                    String href = tag.mAttributes.getValue("href");
                    if (href != null) {
                        spannableString.setSpan(new URLSpan(href), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    break;
                case "annotation":
                    for (int i = 0; i < attrs.getLength(); i++) {
                        String key = attrs.getLocalName(i);
                        String value = attrs.getValue(i);
                        spannableString.setSpan(new Annotation(key, value), start, end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
            }
        }
        return new SpannedString(spannableString);
    }

    // ------- TypedValue stuff
    // This is taken from //device/libs/utils/ResourceTypes.cpp

    private static final class UnitEntry {
        String name;
        int type;
        int unit;
        float scale;

        UnitEntry(String name, int type, int unit, float scale) {
            this.name = name;
            this.type = type;
            this.unit = unit;
            this.scale = scale;
        }
    }

    private static final UnitEntry[] sUnitNames = new UnitEntry[] {
        new UnitEntry("px", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PX, 1.0f),
        new UnitEntry("dip", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
        new UnitEntry("dp", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
        new UnitEntry("sp", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_SP, 1.0f),
        new UnitEntry("pt", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PT, 1.0f),
        new UnitEntry("in", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_IN, 1.0f),
        new UnitEntry("mm", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_MM, 1.0f),
        new UnitEntry("%", TypedValue.TYPE_FRACTION, TypedValue.COMPLEX_UNIT_FRACTION, 1.0f/100),
        new UnitEntry("%p", TypedValue.TYPE_FRACTION, TypedValue.COMPLEX_UNIT_FRACTION_PARENT, 1.0f/100),
    };

    /**
     * Returns the raw value from the given attribute float-type value string.
     * This object is only valid until the next call on to {@link ResourceHelper}.
     */
    public static TypedValue getValue(String attribute, String value, boolean requireUnit) {
        if (parseFloatAttribute(attribute, value, mValue, requireUnit)) {
            return mValue;
        }

        return null;
    }

    /**
     * Parse a float attribute and return the parsed value into a given TypedValue.
     * @param attribute the name of the attribute. Can be null if <var>requireUnit</var> is false.
     * @param value the string value of the attribute
     * @param outValue the TypedValue to receive the parsed value
     * @param requireUnit whether the value is expected to contain a unit.
     * @return true if success.
     */
    public static boolean parseFloatAttribute(String attribute, @NonNull String value,
            TypedValue outValue, boolean requireUnit) {
        assert !requireUnit || attribute != null;

        // remove the space before and after
        value = value.trim();
        int len = value.length();

        if (len <= 0) {
            return false;
        }

        // check that there's no non ascii characters.
        char[] buf = value.toCharArray();
        for (int i = 0 ; i < len ; i++) {
            if (buf[i] > 255) {
                return false;
            }
        }

        // check the first character
        if ((buf[0] < '0' || buf[0] > '9') && buf[0] != '.' && buf[0] != '-' && buf[0] != '+') {
            return false;
        }

        // now look for the string that is after the float...
        Matcher m = sFloatPattern.matcher(value);
        if (m.matches()) {
            String f_str = m.group(1);
            String end = m.group(2);

            float f;
            try {
                f = Float.parseFloat(f_str);
            } catch (NumberFormatException e) {
                // this shouldn't happen with the regexp above.
                return false;
            }

            if (end.length() > 0 && end.charAt(0) != ' ') {
                // Might be a unit...
                if (parseUnit(end, outValue, sFloatOut)) {
                    computeTypedValue(outValue, f, sFloatOut[0]);
                    return true;
                }
                return false;
            }

            // make sure it's only spaces at the end.
            end = end.trim();

            if (end.length() == 0) {
                if (outValue != null) {
                    if (!requireUnit) {
                        outValue.type = TypedValue.TYPE_FLOAT;
                        outValue.data = Float.floatToIntBits(f);
                    } else {
                        // no unit when required? Use dp and out an error.
                        applyUnit(sUnitNames[1], outValue, sFloatOut);
                        computeTypedValue(outValue, f, sFloatOut[0]);

                        Bridge.getLog().error(ILayoutLog.TAG_RESOURCES_RESOLVE,
                                String.format(
                                        "Dimension \"%1$s\" in attribute \"%2$s\" is missing unit!",
                                        value, attribute),
                                null, null);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private static void computeTypedValue(TypedValue outValue, float value, float scale) {
        value *= scale;
        boolean neg = value < 0;
        if (neg) {
            value = -value;
        }
        long bits = (long)(value*(1<<23)+.5f);
        int radix;
        int shift;
        if ((bits&0x7fffff) == 0) {
            // Always use 23p0 if there is no fraction, just to make
            // things easier to read.
            radix = TypedValue.COMPLEX_RADIX_23p0;
            shift = 23;
        } else if ((bits&0xffffffffff800000L) == 0) {
            // Magnitude is zero -- can fit in 0 bits of precision.
            radix = TypedValue.COMPLEX_RADIX_0p23;
            shift = 0;
        } else if ((bits&0xffffffff80000000L) == 0) {
            // Magnitude can fit in 8 bits of precision.
            radix = TypedValue.COMPLEX_RADIX_8p15;
            shift = 8;
        } else if ((bits&0xffffff8000000000L) == 0) {
            // Magnitude can fit in 16 bits of precision.
            radix = TypedValue.COMPLEX_RADIX_16p7;
            shift = 16;
        } else {
            // Magnitude needs entire range, so no fractional part.
            radix = TypedValue.COMPLEX_RADIX_23p0;
            shift = 23;
        }
        int mantissa = (int)(
            (bits>>shift) & TypedValue.COMPLEX_MANTISSA_MASK);
        if (neg) {
            mantissa = (-mantissa) & TypedValue.COMPLEX_MANTISSA_MASK;
        }
        outValue.data |=
            (radix<<TypedValue.COMPLEX_RADIX_SHIFT)
            | (mantissa<<TypedValue.COMPLEX_MANTISSA_SHIFT);
    }

    private static boolean parseUnit(String str, TypedValue outValue, float[] outScale) {
        str = str.trim();

        for (UnitEntry unit : sUnitNames) {
            if (unit.name.equals(str)) {
                applyUnit(unit, outValue, outScale);
                return true;
            }
        }

        return false;
    }

    private static void applyUnit(UnitEntry unit, TypedValue outValue, float[] outScale) {
        outValue.type = unit.type;
        // COMPLEX_UNIT_SHIFT is 0 and hence intelliJ complains about it. Suppress the warning.
        //noinspection PointlessBitwiseExpression
        outValue.data = unit.unit << TypedValue.COMPLEX_UNIT_SHIFT;
        outScale[0] = unit.scale;
    }

    private static class Tag {
        private String mLabel;
        private int mStart;
        private int mEnd;
        private Attributes mAttributes;

        private Tag(String label) {
            mLabel = label;
        }
    }
}

