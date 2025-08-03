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

package com.android.intentresolver.model;

import static junit.framework.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Message;

import androidx.test.InstrumentationRegistry;

import com.android.intentresolver.ResolvedComponentInfo;
import com.android.intentresolver.chooser.TargetInfo;

import com.google.android.collect.Lists;

import org.junit.Test;

import java.util.List;

public class AbstractResolverComparatorTest {

    @Test
    public void testPinned() {
        ResolvedComponentInfo r1 = createResolvedComponentInfo(
                new ComponentName("package", "class"));
        r1.setPinned(true);

        ResolvedComponentInfo r2 = createResolvedComponentInfo(
                new ComponentName("zackage", "zlass"));

        Context context = InstrumentationRegistry.getTargetContext();
        AbstractResolverComparator comparator = getTestComparator(context, null);

        assertEquals("Pinned ranks over unpinned", -1, comparator.compare(r1, r2));
        assertEquals("Unpinned ranks under pinned", 1, comparator.compare(r2, r1));
    }

    @Test
    public void testBothPinned() {
        ResolvedComponentInfo r1 = createResolvedComponentInfo(
                new ComponentName("package", "class"));
        r1.setPinned(true);

        ResolvedComponentInfo r2 = createResolvedComponentInfo(
                new ComponentName("zackage", "zlass"));
        r2.setPinned(true);

        Context context = InstrumentationRegistry.getTargetContext();
        AbstractResolverComparator comparator = getTestComparator(context, null);

        assertEquals("Both pinned should rank alphabetically", -1, comparator.compare(r1, r2));
    }

    @Test
    public void testPromoteToFirst() {
        ComponentName promoteToFirst = new ComponentName("promoted-package", "class");
        ResolvedComponentInfo r1 = createResolvedComponentInfo(promoteToFirst);

        ResolvedComponentInfo r2 = createResolvedComponentInfo(
                new ComponentName("package", "class"));

        Context context = InstrumentationRegistry.getTargetContext();
        AbstractResolverComparator comparator = getTestComparator(context, promoteToFirst);

        assertEquals("PromoteToFirst ranks over non-cemented", -1, comparator.compare(r1, r2));
        assertEquals("Non-cemented ranks under PromoteToFirst", 1, comparator.compare(r2, r1));
    }

    @Test
    public void testPromoteToFirstOverPinned() {
        ComponentName cementedComponent = new ComponentName("promoted-package", "class");
        ResolvedComponentInfo r1 = createResolvedComponentInfo(cementedComponent);

        ResolvedComponentInfo r2 = createResolvedComponentInfo(
                new ComponentName("package", "class"));
        r2.setPinned(true);

        Context context = InstrumentationRegistry.getTargetContext();
        AbstractResolverComparator comparator = getTestComparator(context, cementedComponent);

        assertEquals("PromoteToFirst ranks over pinned", -1, comparator.compare(r1, r2));
        assertEquals("Pinned ranks under PromoteToFirst", 1, comparator.compare(r2, r1));
    }

    private ResolvedComponentInfo createResolvedComponentInfo(ComponentName component) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = component.getPackageName();
        info.activityInfo.name = component.getClassName();
        return new ResolvedComponentInfo(component, new Intent(), info);
    }

    private AbstractResolverComparator getTestComparator(
            Context context, ComponentName promoteToFirst) {
        Intent intent = new Intent();

        AbstractResolverComparator testComparator =
                new AbstractResolverComparator(context, intent,
                        Lists.newArrayList(context.getUser()), promoteToFirst) {

                    @Override
                    public int compare(ResolveInfo lhs, ResolveInfo rhs) {
                        // Used for testing pinning, so we should never get here --- the overrides
                        // should determine the result instead.
                        return 1;
                    }

                    @Override
                    public void doCompute(List<ResolvedComponentInfo> targets) {}

                    @Override
                    public float getScore(TargetInfo targetInfo) {
                        return 0;
                    }

                    @Override
                    public void handleResultMessage(Message message) {}
                };
        return testComparator;
    }

}
