/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.chassis.car.ui.plugin.recyclerview;

import android.text.SpannableString;

import androidx.annotation.NonNull;

import com.android.car.ui.CarUiText;
import com.android.car.ui.plugin.oemapis.Consumer;
import com.android.car.ui.plugin.oemapis.TextOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ContentListItemOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ContentListItemOEMV2;
import com.android.car.ui.plugin.oemapis.recyclerview.HeaderListItemOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ListItemOEMV1;
import com.android.car.ui.recyclerview.CarUiContentListItem;
import com.android.car.ui.recyclerview.CarUiHeaderListItem;
import com.android.car.ui.recyclerview.CarUiListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for converting between static and oem list items.
 */
public final class ListItemUtils {

    /**
     * The plugin was passed the list items as {@code ListItemOEMV1}s and thus must be converted
     * back to use the "original" {@code CarUiListItem}s that's expected by the
     * {@code CarUiListItemAdapter}
     */
    @NonNull
    public static CarUiListItem toStaticListItem(@NonNull ListItemOEMV1 item) {
        if (item instanceof HeaderListItemOEMV1) {
            HeaderListItemOEMV1 header = (HeaderListItemOEMV1) item;
            return new CarUiHeaderListItem(header.getTitle(), header.getBody());
        } else if (item instanceof ContentListItemOEMV1) { // For backwards compatibility
            ContentListItemOEMV1 contentItem = (ContentListItemOEMV1) item;

            CarUiContentListItem listItem = new CarUiContentListItem(
                    toCarUiContentListItemActionV1(contentItem.getAction()));

            if (contentItem.getTitle() != null) {
                listItem.setTitle(toCarUiText(contentItem.getTitle()));
            }

            if (contentItem.getBody() != null) {
                listItem.setBody(toCarUiText(contentItem.getBody()));
            }

            listItem.setIcon(contentItem.getIcon());
            listItem.setPrimaryIconType(
                    toCarUiContentListItemIconTypeV1(contentItem.getPrimaryIconType()));

            if (contentItem.getAction() == ContentListItemOEMV1.Action.ICON) {
                CarUiContentListItem.OnClickListener listener =
                        contentItem.getSupplementalIconOnClickListener() != null
                                ? carUiContentListItem ->
                                contentItem.getSupplementalIconOnClickListener().accept(
                                        contentItem) : null;


                listItem.setSupplementalIcon(contentItem.getSupplementalIcon(), listener);
            }

            if (contentItem.getOnClickListener() != null) {
                CarUiContentListItem.OnClickListener listener =
                        contentItem.getOnClickListener() != null
                                ? carUiContentListItem ->
                                contentItem.getOnClickListener().accept(
                                        contentItem) : null;
                listItem.setOnItemClickedListener(listener);
            }

            // Convert the {@code CarUiContentListItem} provided by the static list item's
            // OnCheckedChangeListener callback to a {@code ListItemOEMV1} so that is is compatible
            // with the provided {@code ListItemOEMV1}'s OnCheckedChangeListener which is of the
            // form Consumer<ListItemOEMV1>
            listItem.setOnCheckedChangeListener((carUiContentListItem, checked) -> {
                carUiContentListItem.setChecked(checked);
                if (contentItem.getOnCheckedChangeListener() != null) {
                    contentItem.getOnCheckedChangeListener().accept(
                            (ContentListItemOEMV1) toOemListItemV1(carUiContentListItem));
                }
            });

            listItem.setActionDividerVisible(contentItem.isActionDividerVisible());
            listItem.setEnabled(contentItem.isEnabled());
            listItem.setChecked(contentItem.isChecked());
            listItem.setActivated(contentItem.isActivated());
            listItem.setSecure(contentItem.isSecure());
            return listItem;
        } else if (item instanceof ContentListItemOEMV2) {
            ContentListItemOEMV2 contentItem = (ContentListItemOEMV2) item;

            CarUiContentListItem listItem = new CarUiContentListItem(
                    toCarUiContentListItemActionV2(contentItem.getAction()));

            if (contentItem.getTitle() != null) {
                listItem.setTitle(toCarUiText(contentItem.getTitle()));
            }

            if (contentItem.getBody() != null) {
                listItem.setBody(toCarUiText(contentItem.getBody()));
            }

            listItem.setIcon(contentItem.getIcon());
            listItem.setPrimaryIconType(
                    toCarUiContentListItemIconTypeV2(contentItem.getPrimaryIconType()));

            if (contentItem.getAction() == ContentListItemOEMV2.Action.ICON) {
                CarUiContentListItem.OnClickListener listener =
                        contentItem.getSupplementalIconOnClickListener() != null
                                ? carUiContentListItem ->
                                contentItem.getSupplementalIconOnClickListener().accept(
                                        contentItem) : null;


                listItem.setSupplementalIcon(contentItem.getSupplementalIcon(), listener);
            }

            if (contentItem.getOnClickListener() != null) {
                CarUiContentListItem.OnClickListener listener =
                        contentItem.getOnClickListener() != null
                                ? carUiContentListItem ->
                                contentItem.getOnClickListener().accept(
                                        contentItem) : null;
                listItem.setOnItemClickedListener(listener);
            }

            // Convert the {@code CarUiContentListItem} provided by the static list item's
            // OnCheckedChangeListener callback to a {@code ListItemOEMV1} so that is is compatible
            // with the provided {@code ListItemOEMV1}'s OnCheckedChangeListener which is of the
            // form Consumer<ListItemOEMV1>
            listItem.setOnCheckedChangeListener((carUiContentListItem, checked) -> {
                carUiContentListItem.setChecked(checked);
                if (contentItem.getOnCheckedChangeListener() != null) {
                    contentItem.getOnCheckedChangeListener().accept(
                            (ContentListItemOEMV2) toOemListItemV2(carUiContentListItem));
                }
            });

            listItem.setActionDividerVisible(contentItem.isActionDividerVisible());
            listItem.setEnabled(contentItem.isEnabled());
            listItem.setChecked(contentItem.isChecked());
            listItem.setActivated(contentItem.isActivated());
            listItem.setSecure(contentItem.isSecure());
            return listItem;
        } else {
            throw new IllegalStateException("Unknown view type.");
        }
    }

    private static CarUiText toCarUiText(TextOEMV1 text) {
        return new CarUiText.Builder(text.getTextVariants()).setMaxChars(
                text.getMaxChars()).setMaxLines(text.getMaxLines()).build();
    }

    private static List<CarUiText> toCarUiText(List<TextOEMV1> lines) {
        List<CarUiText> oemLines = new ArrayList<>();

        for (TextOEMV1 line : lines) {
            oemLines.add(new CarUiText.Builder(line.getTextVariants()).setMaxChars(
                    line.getMaxChars()).setMaxLines(line.getMaxLines()).build());
        }
        return oemLines;
    }

    // For backwards compatibility
    private static CarUiContentListItem.Action toCarUiContentListItemActionV1(
            ContentListItemOEMV1.Action action) {
        switch (action) {
            case NONE:
                return CarUiContentListItem.Action.NONE;
            case SWITCH:
                return CarUiContentListItem.Action.SWITCH;
            case CHECK_BOX:
                return CarUiContentListItem.Action.CHECK_BOX;
            case RADIO_BUTTON:
                return CarUiContentListItem.Action.RADIO_BUTTON;
            case ICON:
                return CarUiContentListItem.Action.ICON;
            case CHEVRON:
                return CarUiContentListItem.Action.CHEVRON;
            default:
                throw new IllegalStateException("Unexpected list item action type");
        }
    }

    private static CarUiContentListItem.Action toCarUiContentListItemActionV2(
            ContentListItemOEMV2.Action action) {
        switch (action) {
            case NONE:
                return CarUiContentListItem.Action.NONE;
            case SWITCH:
                return CarUiContentListItem.Action.SWITCH;
            case CHECK_BOX:
                return CarUiContentListItem.Action.CHECK_BOX;
            case RADIO_BUTTON:
                return CarUiContentListItem.Action.RADIO_BUTTON;
            case ICON:
                return CarUiContentListItem.Action.ICON;
            case CHEVRON:
                return CarUiContentListItem.Action.CHEVRON;
            default:
                throw new IllegalStateException("Unexpected list item action type");
        }
    }

    // For backwards compatibility
    private static CarUiContentListItem.IconType toCarUiContentListItemIconTypeV1(
            ContentListItemOEMV1.IconType iconType) {
        switch (iconType) {
            case CONTENT:
                return CarUiContentListItem.IconType.CONTENT;
            case STANDARD:
                return CarUiContentListItem.IconType.STANDARD;
            case AVATAR:
                return CarUiContentListItem.IconType.AVATAR;
            default:
                throw new IllegalStateException("Unexpected list item icon type");
        }
    }

    private static CarUiContentListItem.IconType toCarUiContentListItemIconTypeV2(
            ContentListItemOEMV2.IconType iconType) {
        switch (iconType) {
            case CONTENT:
                return CarUiContentListItem.IconType.CONTENT;
            case STANDARD:
                return CarUiContentListItem.IconType.STANDARD;
            case AVATAR:
                return CarUiContentListItem.IconType.AVATAR;
            default:
                throw new IllegalStateException("Unexpected list item icon type");
        }
    }

    // Below methods are necessary to convert from a static list item to an oem list item, which is
    // needed to set a static list item's OnCheckedChangeListener
    private static ListItemOEMV1 toOemListItemV2(CarUiListItem item) {
        if (item instanceof CarUiHeaderListItem) {
            CarUiHeaderListItem header = (CarUiHeaderListItem) item;
            return new HeaderListItemOEMV1.Builder(new SpannableString(header.getTitle()))
                    .setBody(new SpannableString(header.getBody()))
                    .build();
        } else if (item instanceof CarUiContentListItem) {
            CarUiContentListItem contentItem = (CarUiContentListItem) item;

            ContentListItemOEMV2.Builder builder = new ContentListItemOEMV2.Builder(
                    toOemListItemActionV2(contentItem.getAction()));

            if (contentItem.getTitle() != null) {
                builder.setTitle(toOemText(contentItem.getTitle()));
            }

            if (contentItem.getBody() != null) {
                builder.setBody(toOemText(contentItem.getBody()));
            }

            builder.setIcon(contentItem.getIcon(),
                    toOemListItemIconTypeV2(contentItem.getPrimaryIconType()));

            if (contentItem.getAction() == CarUiContentListItem.Action.ICON) {
                Consumer<ContentListItemOEMV2> listener =
                        contentItem.getSupplementalIconOnClickListener() != null
                                ? oemItem ->
                                contentItem.getSupplementalIconOnClickListener().onClick(
                                        contentItem) : null;
                builder.setSupplementalIcon(contentItem.getSupplementalIcon(), listener);
            }

            if (contentItem.getOnClickListener() != null) {
                Consumer<ContentListItemOEMV2> listener =
                        contentItem.getOnClickListener() != null
                                ? oemItem ->
                                contentItem.getOnClickListener().onClick(contentItem) : null;
                builder.setOnItemClickedListener(listener);
            }

            builder.setOnCheckedChangeListener(oem -> contentItem.setChecked(oem.isChecked()))
                    .setActionDividerVisible(contentItem.isActionDividerVisible())
                    .setEnabled(contentItem.isEnabled())
                    .setChecked(contentItem.isChecked())
                    .setActivated(contentItem.isActivated())
                    .setSecure(contentItem.isSecure());
            return builder.build();
        } else {
            throw new IllegalStateException("Unknown view type.");
        }
    }

    // For backwards compatibility
    private static ListItemOEMV1 toOemListItemV1(CarUiListItem item) {
        if (item instanceof CarUiHeaderListItem) {
            CarUiHeaderListItem header = (CarUiHeaderListItem) item;
            return new HeaderListItemOEMV1.Builder(new SpannableString(header.getTitle()))
                    .setBody(new SpannableString(header.getBody()))
                    .build();
        } else if (item instanceof CarUiContentListItem) {
            CarUiContentListItem contentItem = (CarUiContentListItem) item;

            ContentListItemOEMV1.Builder builder = new ContentListItemOEMV1.Builder(
                    toOemListItemActionV1(contentItem.getAction()));

            if (contentItem.getTitle() != null) {
                builder.setTitle(toOemText(contentItem.getTitle()));
            }

            if (contentItem.getBody() != null) {
                builder.setBody(toOemText(contentItem.getBody()));
            }

            builder.setIcon(contentItem.getIcon(),
                    toOemListItemIconTypeV1(contentItem.getPrimaryIconType()));

            if (contentItem.getAction() == CarUiContentListItem.Action.ICON) {
                java.util.function.Consumer<ContentListItemOEMV1> listener =
                        contentItem.getSupplementalIconOnClickListener() != null
                                ? oemItem ->
                                contentItem.getSupplementalIconOnClickListener().onClick(
                                        contentItem) : null;
                builder.setSupplementalIcon(contentItem.getSupplementalIcon(), listener);
            }

            if (contentItem.getOnClickListener() != null) {
                java.util.function.Consumer<ContentListItemOEMV1> listener =
                        contentItem.getOnClickListener() != null
                                ? oemItem ->
                                contentItem.getOnClickListener().onClick(contentItem) : null;
                builder.setOnItemClickedListener(listener);
            }

            builder.setOnCheckedChangeListener(oem -> contentItem.setChecked(oem.isChecked()))
                    .setActionDividerVisible(contentItem.isActionDividerVisible())
                    .setEnabled(contentItem.isEnabled())
                    .setChecked(contentItem.isChecked())
                    .setActivated(contentItem.isActivated())
                    .setSecure(contentItem.isSecure());
            return builder.build();
        } else {
            throw new IllegalStateException("Unknown view type.");
        }
    }

    private static TextOEMV1 toOemText(CarUiText text) {
        return new TextOEMV1.Builder(text.getTextVariants()).setMaxChars(
                text.getMaxChars()).setMaxLines(text.getMaxLines()).build();
    }

    private static List<TextOEMV1> toOemText(List<CarUiText> lines) {
        List<TextOEMV1> oemLines = new ArrayList<>();

        for (CarUiText line : lines) {
            oemLines.add(new TextOEMV1.Builder(line.getTextVariants()).setMaxChars(
                    line.getMaxChars()).setMaxLines(line.getMaxLines()).build());
        }
        return oemLines;
    }

    private static ContentListItemOEMV2.Action toOemListItemActionV2(
            CarUiContentListItem.Action action) {
        switch (action) {
            case NONE:
                return ContentListItemOEMV2.Action.NONE;
            case SWITCH:
                return ContentListItemOEMV2.Action.SWITCH;
            case CHECK_BOX:
                return ContentListItemOEMV2.Action.CHECK_BOX;
            case RADIO_BUTTON:
                return ContentListItemOEMV2.Action.RADIO_BUTTON;
            case ICON:
                return ContentListItemOEMV2.Action.ICON;
            case CHEVRON:
                return ContentListItemOEMV2.Action.CHEVRON;
            default:
                throw new IllegalStateException("Unexpected list item action type");
        }
    }

    private static ContentListItemOEMV2.IconType toOemListItemIconTypeV2(
            CarUiContentListItem.IconType iconType) {
        switch (iconType) {
            case CONTENT:
                return ContentListItemOEMV2.IconType.CONTENT;
            case STANDARD:
                return ContentListItemOEMV2.IconType.STANDARD;
            case AVATAR:
                return ContentListItemOEMV2.IconType.AVATAR;
            default:
                throw new IllegalStateException("Unexpected list item icon type");
        }
    }

    // For backwards compatibility
    private static ContentListItemOEMV1.Action toOemListItemActionV1(
            CarUiContentListItem.Action action) {
        switch (action) {
            case NONE:
                return ContentListItemOEMV1.Action.NONE;
            case SWITCH:
                return ContentListItemOEMV1.Action.SWITCH;
            case CHECK_BOX:
                return ContentListItemOEMV1.Action.CHECK_BOX;
            case RADIO_BUTTON:
                return ContentListItemOEMV1.Action.RADIO_BUTTON;
            case ICON:
                return ContentListItemOEMV1.Action.ICON;
            case CHEVRON:
                return ContentListItemOEMV1.Action.CHEVRON;
            default:
                throw new IllegalStateException("Unexpected list item action type");
        }
    }

    // For backwards compatibility
    private static ContentListItemOEMV1.IconType toOemListItemIconTypeV1(
            CarUiContentListItem.IconType iconType) {
        switch (iconType) {
            case CONTENT:
                return ContentListItemOEMV1.IconType.CONTENT;
            case STANDARD:
                return ContentListItemOEMV1.IconType.STANDARD;
            case AVATAR:
                return ContentListItemOEMV1.IconType.AVATAR;
            default:
                throw new IllegalStateException("Unexpected list item icon type");
        }
    }
}
