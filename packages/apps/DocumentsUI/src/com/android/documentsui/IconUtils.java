/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.android.documentsui.base.UserId;

import java.util.Locale;

public class IconUtils {
    public static Drawable loadPackageIcon(Context context, UserId userId, String authority,
            int icon, boolean maybeShowBadge) {
        if (icon != 0) {
            final PackageManager pm = userId.getPackageManager(context);
            Drawable packageIcon = null;
            if (authority != null) {
                final ProviderInfo info = pm.resolveContentProvider(authority, 0);
                if (info != null) {
                    packageIcon = pm.getDrawable(info.packageName, icon, info.applicationInfo);
                }
            } else {
                packageIcon = userId.getDrawable(context, icon);
            }
            if (packageIcon != null && maybeShowBadge) {
                return userId.getUserBadgedIcon(context, packageIcon);
            } else {
                return packageIcon;
            }
        }

        return null;
    }

    public static Drawable loadMimeIcon(
            Context context, String mimeType, String authority, String docId, int mode) {
        return loadMimeIcon(context, mimeType);
    }

    /**
     * Load mime type drawable from system MimeIconUtils.
     * @param context activity context to obtain resource
     * @param mimeType specific mime type string of file
     * @return drawable of mime type files from system default
     */
    public static Drawable loadMimeIcon(Context context, String mimeType) {
        if (mimeType == null) return null;
        return context.getContentResolver().getTypeInfo(mimeType).getIcon().loadDrawable(context);
    }

    public static Drawable applyTintColor(Context context, int drawableId, int tintColorId) {
        final Drawable icon = context.getDrawable(drawableId);
        return applyTintColor(context, icon, tintColorId);
    }

    public static Drawable applyTintColor(Context context, Drawable icon, int tintColorId) {
        icon.mutate();
        icon.setTintList(context.getColorStateList(tintColorId));
        return icon;
    }

    public static Drawable applyTintAttr(Context context, int drawableId, int tintAttrId) {
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(tintAttrId, outValue, true);
        return applyTintColor(context, drawableId, outValue.resourceId);
    }

    public static int getIconResId(String mimeType) {
        mimeType = mimeType.toLowerCase(Locale.US);

        switch (mimeType) {
            case "inode/directory":
            case "vnd.android.document/directory":
                return R.drawable.ic_doc_folder;

            case "application/vnd.android.package-archive":
                return R.drawable.ic_doc_apk;

            case "application/pgp-keys":
            case "application/pgp-signature":
            case "application/x-pkcs12":
            case "application/x-pkcs7-certreqresp":
            case "application/x-pkcs7-crl":
            case "application/x-x509-ca-cert":
            case "application/x-x509-user-cert":
            case "application/x-pkcs7-certificates":
            case "application/x-pkcs7-mime":
            case "application/x-pkcs7-signature":
                return R.drawable.ic_doc_certificate;

            case "application/rdf+xml":
            case "application/rss+xml":
            case "application/x-object":
            case "application/xhtml+xml":
            case "text/css":
            case "text/html":
            case "text/xml":
            case "text/x-c++hdr":
            case "text/x-c++src":
            case "text/x-chdr":
            case "text/x-csrc":
            case "text/x-dsrc":
            case "text/x-csh":
            case "text/x-haskell":
            case "text/x-java":
            case "text/x-literate-haskell":
            case "text/x-pascal":
            case "text/x-tcl":
            case "text/x-tex":
            case "application/x-latex":
            case "application/x-texinfo":
            case "application/atom+xml":
            case "application/ecmascript":
            case "application/json":
            case "application/javascript":
            case "application/xml":
            case "text/javascript":
            case "application/x-javascript":
                return R.drawable.ic_doc_codes;

            case "application/mac-binhex40":
            case "application/rar":
            case "application/zip":
            case "application/x-apple-diskimage":
            case "application/x-debian-package":
            case "application/x-gtar":
            case "application/x-iso9660-image":
            case "application/x-lha":
            case "application/x-lzh":
            case "application/x-lzx":
            case "application/x-stuffit":
            case "application/x-tar":
            case "application/x-webarchive":
            case "application/x-webarchive-xml":
            case "application/gzip":
            case "application/x-7z-compressed":
            case "application/x-deb":
            case "application/x-rar-compressed":
                return R.drawable.ic_doc_compressed;

            case "text/x-vcard":
            case "text/vcard":
                return R.drawable.ic_doc_contact;

            case "text/calendar":
            case "text/x-vcalendar":
                return R.drawable.ic_doc_event;

            case "application/x-font":
            case "application/font-woff":
            case "application/x-font-woff":
            case "application/x-font-ttf":
                return R.drawable.ic_doc_font;

            case "application/vnd.oasis.opendocument.graphics":
            case "application/vnd.oasis.opendocument.graphics-template":
            case "application/vnd.oasis.opendocument.image":
            case "application/vnd.stardivision.draw":
            case "application/vnd.sun.xml.draw":
            case "application/vnd.sun.xml.draw.template":
            case "application/vnd.google-apps.drawing":
                return R.drawable.ic_doc_image;

            case "application/pdf":
                return R.drawable.ic_doc_pdf;

            case "application/vnd.stardivision.impress":
            case "application/vnd.sun.xml.impress":
            case "application/vnd.sun.xml.impress.template":
            case "application/x-kpresenter":
            case "application/vnd.oasis.opendocument.presentation":
            case "application/vnd.google-apps.presentation":
                return R.drawable.ic_doc_presentation;

            case "application/vnd.oasis.opendocument.spreadsheet":
            case "application/vnd.oasis.opendocument.spreadsheet-template":
            case "application/vnd.stardivision.calc":
            case "application/vnd.sun.xml.calc":
            case "application/vnd.sun.xml.calc.template":
            case "application/x-kspread":
            case "application/vnd.google-apps.spreadsheet":
                return R.drawable.ic_doc_spreadsheet;

            case "application/vnd.oasis.opendocument.text":
            case "application/vnd.oasis.opendocument.text-master":
            case "application/vnd.oasis.opendocument.text-template":
            case "application/vnd.oasis.opendocument.text-web":
            case "application/vnd.stardivision.writer":
            case "application/vnd.stardivision.writer-global":
            case "application/vnd.sun.xml.writer":
            case "application/vnd.sun.xml.writer.global":
            case "application/vnd.sun.xml.writer.template":
            case "application/x-abiword":
            case "application/x-kword":
            case "application/vnd.google-apps.document":
                return R.drawable.ic_doc_document;

            case "application/x-quicktimeplayer":
            case "application/x-shockwave-flash":
                return R.drawable.ic_doc_video;

            case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.template":
                return R.drawable.ic_doc_word;

            case "application/vnd.ms-excel":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.template":
                return R.drawable.ic_doc_excel;

            case "application/vnd.ms-powerpoint":
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "application/vnd.openxmlformats-officedocument.presentationml.template":
            case "application/vnd.openxmlformats-officedocument.presentationml.slideshow":
                return R.drawable.ic_doc_powerpoint;

            default:
                return getGenericIconResId(mimeType);
        }
    }

    private static int getGenericIconResId(String mimeType) {
        // Look for partial matches
        if (mimeType.startsWith("audio/")) {
            return R.drawable.ic_doc_audio;
        } else if (mimeType.startsWith("video/")) {
            return R.drawable.ic_doc_video;
        } else if (mimeType.startsWith("image/")) {
            return R.drawable.ic_doc_image;
        } else if (mimeType.startsWith("text/")) {
            return R.drawable.ic_doc_text;
        }

        // Worst case, return a generic file
        return R.drawable.ic_doc_generic;
    }
}
