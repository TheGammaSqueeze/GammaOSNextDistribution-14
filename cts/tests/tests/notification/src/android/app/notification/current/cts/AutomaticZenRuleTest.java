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

package android.app.notification.current.cts;

import static android.app.AutomaticZenRule.TYPE_BEDTIME;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.AutomaticZenRule;
import android.app.Flags;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.service.notification.ZenDeviceEffects;
import android.service.notification.ZenPolicy;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutomaticZenRuleTest {

    private final String mName = "name";
    private final ComponentName mOwner = new ComponentName("pkg", "cls");
    private final ComponentName mConfigActivity = new ComponentName("pkg", "act");
    private final ZenPolicy mPolicy = new ZenPolicy.Builder().allowAlarms(true).build();
    private final Uri mConditionId = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();
    private final String mTriggerDescription = "Every Night, 10pm to 6am";
    private final int mType = TYPE_BEDTIME;
    private final boolean mAllowManualInvocation = true;
    private final int mIconResId = 123;
    private final int mInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
    private final boolean mEnabled = true;

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        assertEquals(expected, rule.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        if (Flags.modesApi()) {
            rule.setDeviceEffects(
                    new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build());
        }
        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AutomaticZenRule rule1 = new AutomaticZenRule(parcel);
        assertEquals(mName, rule1.getName());
        assertEquals(mOwner, rule1.getOwner());
        assertEquals(mConditionId, rule1.getConditionId());
        assertEquals(mInterruptionFilter, rule1.getInterruptionFilter());
        assertEquals(mEnabled, rule1.isEnabled());
        assertEquals(mPolicy, rule1.getZenPolicy());
        if (Flags.modesApi()) {
            assertEquals(new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build(),
                    rule1.getDeviceEffects());
        }
        assertEquals(mConfigActivity, rule1.getConfigurationActivity());

        rule.setName(null);
        parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        rule1 = new AutomaticZenRule(parcel);
        assertNull(rule1.getName());
    }

    @Test
    public void testSetConditionId() {
        final Uri newConditionId = new Uri.Builder().scheme("scheme")
                .authority("authority2")
                .appendPath("3path")
                .appendPath("test4")
                .build();
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        rule.setConditionId(newConditionId);
        assertEquals(newConditionId, rule.getConditionId());
    }

    @Test
    public void testSetEnabled() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        rule.setEnabled(!mEnabled);
        assertEquals(!mEnabled, rule.isEnabled());
    }

    @Test
    public void testSetInterruptionFilter() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        for (int i = NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
             i <= NotificationManager.INTERRUPTION_FILTER_ALARMS; i++) {
            rule.setInterruptionFilter(i);
            assertEquals(i, rule.getInterruptionFilter());
        }
    }

    @Test
    public void testSetName() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        rule.setName(mName + "new");
        assertEquals(mName + "new", rule.getName());
    }

    @Test
    public void testSetConfigurationActivity() {
        ComponentName newConfigActivity = new ComponentName("pkg", "new!");
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                mPolicy, mInterruptionFilter, mEnabled);
        rule.setConfigurationActivity(newConfigActivity);
        assertEquals(newConfigActivity, rule.getConfigurationActivity());
    }

    @Test
    public void testCreateRuleWithZenPolicy() {
        ZenPolicy.Builder builder = new ZenPolicy.Builder();
        ZenPolicy policy = builder.build();
        builder.allowAlarms(true);
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigActivity, mConditionId,
                policy, mInterruptionFilter, mEnabled);
        assertEquals(mInterruptionFilter, rule.getInterruptionFilter());
        assertEquals(rule.getZenPolicy(), policy);
    }

    @Test
    public void testBuilder() {
        if (!Flags.modesApi()) {
            return;
        }
        AutomaticZenRule rule1 = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .setEnabled(mEnabled)
                .build();
        assertEquals(mName, rule1.getName());
        assertEquals(mOwner, rule1.getOwner());
        assertEquals(mConditionId, rule1.getConditionId());
        assertEquals(mInterruptionFilter, rule1.getInterruptionFilter());
        assertEquals(mEnabled, rule1.isEnabled());
        assertEquals(mPolicy, rule1.getZenPolicy());
        assertEquals(new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build(),
                rule1.getDeviceEffects());
        assertEquals(mConfigActivity, rule1.getConfigurationActivity());
        assertEquals(mType, rule1.getType());
        assertEquals(mAllowManualInvocation, rule1.isManualInvocationAllowed());
        assertEquals(mTriggerDescription, rule1.getTriggerDescription());
        assertEquals(mIconResId, rule1.getIconResId());
    }

    @Test
    public void testBuilder_fromInstance() {
        if (!Flags.modesApi()) {
            return;
        }
        AutomaticZenRule rule1 = new AutomaticZenRule.Builder(
                new AutomaticZenRule.Builder(mName, mConditionId)
                        .setZenPolicy(mPolicy)
                        .setDeviceEffects(
                                new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                        .setManualInvocationAllowed(mAllowManualInvocation)
                        .setOwner(mOwner)
                        .setType(mType)
                        .setConfigurationActivity(mConfigActivity)
                        .setInterruptionFilter(mInterruptionFilter)
                        .setTriggerDescription(mTriggerDescription)
                        .setIconResId(mIconResId)
                        .setEnabled(mEnabled)
                        .build())
                .build();
        assertEquals(mName, rule1.getName());
        assertEquals(mOwner, rule1.getOwner());
        assertEquals(mConditionId, rule1.getConditionId());
        assertEquals(mInterruptionFilter, rule1.getInterruptionFilter());
        assertEquals(mEnabled, rule1.isEnabled());
        assertEquals(mPolicy, rule1.getZenPolicy());
        assertEquals(new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build(),
                rule1.getDeviceEffects());
        assertEquals(mConfigActivity, rule1.getConfigurationActivity());
        assertEquals(mType, rule1.getType());
        assertEquals(mAllowManualInvocation, rule1.isManualInvocationAllowed());
        assertEquals(mTriggerDescription, rule1.getTriggerDescription());
        assertEquals(mIconResId, rule1.getIconResId());
    }

    @Test
    public void testWriteToParcelFromBuilder() {
        if (!Flags.modesApi()) {
            return;
        }
        AutomaticZenRule rule = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .setEnabled(mEnabled)
                .build();
        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AutomaticZenRule rule1 = new AutomaticZenRule(parcel);
        assertEquals(mName, rule1.getName());
        assertEquals(mOwner, rule1.getOwner());
        assertEquals(mConditionId, rule1.getConditionId());
        assertEquals(mInterruptionFilter, rule1.getInterruptionFilter());
        assertEquals(mEnabled, rule1.isEnabled());
        assertEquals(mPolicy, rule1.getZenPolicy());
        assertEquals(new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build(),
                rule1.getDeviceEffects());
        assertEquals(mConfigActivity, rule1.getConfigurationActivity());
        assertEquals(mType, rule1.getType());
        assertEquals(mAllowManualInvocation, rule1.isManualInvocationAllowed());
        assertEquals(mIconResId, rule1.getIconResId());
        assertTrue(rule1.getTriggerDescription().startsWith(mTriggerDescription));
    }

    @Test
    public void testEquals() {
        if (!Flags.modesApi()) {
            return;
        }
        AutomaticZenRule rule = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .build();
        AutomaticZenRule rule2 = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .build();
        assertEquals(rule, rule2);
    }

    @Test
    public void testHashCode() {
        if (!Flags.modesApi()) {
            return;
        }
        AutomaticZenRule rule = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .build();
        AutomaticZenRule rule2 = new AutomaticZenRule.Builder(mName, mConditionId)
                .setZenPolicy(mPolicy)
                .setDeviceEffects(
                        new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build())
                .setManualInvocationAllowed(mAllowManualInvocation)
                .setOwner(mOwner)
                .setType(mType)
                .setConfigurationActivity(mConfigActivity)
                .setInterruptionFilter(mInterruptionFilter)
                .setTriggerDescription(mTriggerDescription)
                .setIconResId(mIconResId)
                .build();
        assertEquals(rule.hashCode(), rule2.hashCode());
    }
}
