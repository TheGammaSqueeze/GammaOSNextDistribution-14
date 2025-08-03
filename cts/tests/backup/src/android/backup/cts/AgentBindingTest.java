/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.backup.cts;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.testng.Assert.expectThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Scanner;

@AppModeFull
public class AgentBindingTest extends BaseBackupCtsTest {
    private static final String FULL_BACKUP_PACKAGE_NAME = "android.backup.app";
    private static final String KEY_VALUE_BACKUP_PACKAGE_NAME = "android.backup.kvapp";
    private static final ComponentName FULL_BACKUP_AGENT_NAME = ComponentName.createRelative(
            FULL_BACKUP_PACKAGE_NAME, ".FullBackupBackupAgent");
    private static final ComponentName KEY_VALUE_BACKUP_AGENT_NAME = ComponentName.createRelative(
            KEY_VALUE_BACKUP_PACKAGE_NAME, "android.backup.app.KeyValueBackupAgent");
    private static final int LOCAL_TRANSPORT_CONFORMING_FILE_SIZE = 5 * 1024;

    private Context mContext;

    // Save the states before running tests. Restore them after tests finished.
    private int mFullBackupAgentEnabledState;
    private int mKeyValueBackupAgentEnabledState;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mFullBackupAgentEnabledState = mContext.getPackageManager().getComponentEnabledSetting(
                FULL_BACKUP_AGENT_NAME);
        mKeyValueBackupAgentEnabledState = mContext.getPackageManager().getComponentEnabledSetting(
                KEY_VALUE_BACKUP_AGENT_NAME);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        setComponentEnabledSetting(FULL_BACKUP_AGENT_NAME, mFullBackupAgentEnabledState);
        setComponentEnabledSetting(KEY_VALUE_BACKUP_AGENT_NAME, mKeyValueBackupAgentEnabledState);
    }

    public void testUnbindBackupAgent_isNotCallableFromCts() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        expectThrows(Exception.class, () -> unbindBackupAgent(mContext.getApplicationInfo()));
    }

    public void testBindBackupAgent_isNotCallableFromCts() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        expectThrows(Exception.class, () -> bindBackupAgent(mContext.getPackageName(), 0, 0));
    }

    public void testFullBackupAgentComponentDisabled() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setComponentEnabledSetting(FULL_BACKUP_AGENT_NAME, COMPONENT_ENABLED_STATE_DISABLED);
        // Make sure there's something to backup
        createTestFileOfSize(FULL_BACKUP_PACKAGE_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        runBackupAndAssertAgentError(FULL_BACKUP_PACKAGE_NAME);
    }

    public void testKeyValueBackupAgentComponentDisabled() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setComponentEnabledSetting(KEY_VALUE_BACKUP_AGENT_NAME, COMPONENT_ENABLED_STATE_DISABLED);
        // Make sure there's something to backup
        createTestFileOfSize(KEY_VALUE_BACKUP_PACKAGE_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        runBackupAndAssertAgentError(KEY_VALUE_BACKUP_PACKAGE_NAME);
    }

    private void setComponentEnabledSetting(ComponentName componentName, int enabledState)
            throws IOException {
        final StringBuilder cmd = new StringBuilder("pm ");
        switch (enabledState) {
            case COMPONENT_ENABLED_STATE_DEFAULT:
                cmd.append("default-state ");
                break;
            case COMPONENT_ENABLED_STATE_ENABLED:
                cmd.append("enable ");
                break;
            case COMPONENT_ENABLED_STATE_DISABLED:
                cmd.append("disable ");
                break;
            case COMPONENT_ENABLED_STATE_DISABLED_USER:
                cmd.append("disable-user ");
                break;
            case COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                cmd.append("disable-until-used ");
                break;
            default:
                throw new IllegalArgumentException("Invalid enabled state:" + enabledState);
        }
        cmd.append("--user cur ").append(componentName.flattenToString());
        getBackupUtils().executeShellCommandSync(cmd.toString());
    }

    /**
     * Parses the output of "bmgr backupnow" command and checks an agent error during
     * backup / restore.
     */
    private void runBackupAndAssertAgentError(String packageName) throws IOException {
        Scanner in = new Scanner(getBackupUtils().getBackupNowOutput(packageName));
        boolean found = false;
        while (in.hasNextLine()) {
            String line = in.nextLine();

            if (line.contains(packageName)) {
                String result = line.split(":")[1].trim();
                if ("Agent error".equals(result)) {
                    found = true;
                    break;
                }
            }
        }
        in.close();
        assertTrue("Didn't find \'Agent error\' in the output", found);
    }

    private static void unbindBackupAgent(ApplicationInfo applicationInfo) throws Exception {
        callActivityManagerMethod(
                "unbindBackupAgent",
                new Class<?>[] {ApplicationInfo.class},
                new Object[] {applicationInfo});
    }

    private static void bindBackupAgent(String packageName, int backupRestoreMode, int userId)
            throws Exception {
        callActivityManagerMethod(
                "bindBackupAgent",
                new Class<?>[] {String.class, int.class, int.class},
                new Object[] {packageName, backupRestoreMode, userId});
    }

    private static void callActivityManagerMethod(
            String methodName, Class<?>[] types, Object[] args) throws Exception {
        Class<?> activityManagerClass = Class.forName("android.app.IActivityManager");
        Object activityManager = getActivityManager();
        Method bindBackupAgentMethod = activityManagerClass.getMethod(methodName, types);
        bindBackupAgentMethod.invoke(activityManager, args);
    }

    private static Object getActivityManager() throws Exception {
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Class<?> stubClass = Class.forName("android.app.IActivityManager$Stub");
        Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
        Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
        return asInterfaceMethod.invoke(
                null, (IBinder) getServiceMethod.invoke(serviceManagerClass, "activity"));
    }
}
