/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.model.account;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Relation;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.CommonDateUtils;
import com.android.contactsbind.FeedbackHelper;

import com.google.common.collect.Lists;

public class DeviceLocalAccountType extends FallbackAccountType {

    private static final String TAG = "DeviceLocalAccountType";

    private final boolean mGroupsEditable;

    public DeviceLocalAccountType(Context context, boolean groupsEditable) {
        super(context);

        try {
            addDataKindRelation(context);
            addDataKindEvent(context);
        } catch (DefinitionException e) {
            FeedbackHelper.sendFeedback(context, TAG, "Failed to build fallback account type", e);
        }

        mGroupsEditable = groupsEditable;
    }

    public DeviceLocalAccountType(Context context) {
        this(context, false);
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return mGroupsEditable;
    }

    @Override
    public AccountInfo wrapAccount(Context context, AccountWithDataSet account) {
        // Use the "Device" type label for the name as well because on OEM phones the "name" is
        // not always user-friendly
        return new AccountInfo(
                new AccountDisplayInfo(account, getDisplayLabel(context), getDisplayLabel(context),
                        getDisplayIcon(context), true), this);
    }

    private DataKind addDataKindRelation(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Relation.CONTENT_ITEM_TYPE,
                R.string.relationLabelsGroup, Weight.RELATIONSHIP, true));
        kind.actionHeader = new RelationActionInflater();
        kind.actionBody = new SimpleInflater(Relation.NAME);

        kind.typeColumn = Relation.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildRelationType(Relation.TYPE_ASSISTANT));
        kind.typeList.add(buildRelationType(Relation.TYPE_BROTHER));
        kind.typeList.add(buildRelationType(Relation.TYPE_CHILD));
        kind.typeList.add(buildRelationType(Relation.TYPE_DOMESTIC_PARTNER));
        kind.typeList.add(buildRelationType(Relation.TYPE_FATHER));
        kind.typeList.add(buildRelationType(Relation.TYPE_FRIEND));
        kind.typeList.add(buildRelationType(Relation.TYPE_MANAGER));
        kind.typeList.add(buildRelationType(Relation.TYPE_MOTHER));
        kind.typeList.add(buildRelationType(Relation.TYPE_PARENT));
        kind.typeList.add(buildRelationType(Relation.TYPE_PARTNER));
        kind.typeList.add(buildRelationType(Relation.TYPE_REFERRED_BY));
        kind.typeList.add(buildRelationType(Relation.TYPE_RELATIVE));
        kind.typeList.add(buildRelationType(Relation.TYPE_SISTER));
        kind.typeList.add(buildRelationType(Relation.TYPE_SPOUSE));
        kind.typeList.add(buildRelationType(Relation.TYPE_CUSTOM).setSecondary(true)
                .setCustomColumn(Relation.LABEL));

        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Relation.TYPE, Relation.TYPE_SPOUSE);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Relation.DATA, R.string.relationLabelsGroup,
                FLAGS_RELATION));

        return kind;
    }

    private DataKind addDataKindEvent(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Event.CONTENT_ITEM_TYPE,
                    R.string.eventLabelsGroup, Weight.EVENT, true));
        kind.actionHeader = new EventActionInflater();
        kind.actionBody = new SimpleInflater(Event.START_DATE);

        kind.typeColumn = Event.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.dateFormatWithoutYear = CommonDateUtils.NO_YEAR_DATE_FORMAT;
        kind.dateFormatWithYear = CommonDateUtils.FULL_DATE_FORMAT;
        kind.typeList.add(buildEventType(Event.TYPE_BIRTHDAY, true).setSpecificMax(1));
        kind.typeList.add(buildEventType(Event.TYPE_ANNIVERSARY, false));
        kind.typeList.add(buildEventType(Event.TYPE_OTHER, false));
        kind.typeList.add(buildEventType(Event.TYPE_CUSTOM, false).setSecondary(true)
                .setCustomColumn(Event.LABEL));

        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Event.TYPE, Event.TYPE_BIRTHDAY);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Event.DATA, R.string.eventLabelsGroup, FLAGS_EVENT));

        return kind;
    }
}
