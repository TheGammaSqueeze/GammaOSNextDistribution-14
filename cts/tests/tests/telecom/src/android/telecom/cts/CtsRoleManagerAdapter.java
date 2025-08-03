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
 * limitations under the License.
 */

package android.telecom.cts;

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CtsRoleManagerAdapter {

    private static final String TAG = CtsRoleManagerAdapter.class.getSimpleName();

    private Context mContext;
    private RoleManager mRoleManager;
    private Instrumentation mInstrumentation;
    private ConcurrentHashMap<String, List<String>> mRoleHolders;

    public CtsRoleManagerAdapter(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mContext = instrumentation.getContext();
        mRoleManager = (RoleManager) mContext.getSystemService(Context.ROLE_SERVICE);
        mRoleHolders = new ConcurrentHashMap<>();
    }

    public boolean isDialerRoleAvailable() {
        return mRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER);
    }

    public void setDialerRoleHolder(String packageName)
            throws Exception {
        setRoleHolder(RoleManager.ROLE_DIALER, packageName);
    }

    public void setRoleHolder(String role, String packageName) throws Exception {
        if (mRoleManager != null) {
            addRoleHolder(role, packageName);
        } else {
            fail("Expected role manager");
        }
    }

    public void setByPassRoleQualification(boolean bypass) throws Exception {
        if (mRoleManager != null) {
            runWithShellPermissionIdentity(
                    () -> mRoleManager.setBypassingRoleQualification(bypass));
        } else {
            fail("expected role manager");
        }
    }

    public void removeDialerRoleHolder(String packageName) throws Exception {
        removeRoleHolder(RoleManager.ROLE_DIALER, packageName);
    }

    public void removeRoleHolder(String role, String packageName) throws Exception {
        if (mRoleManager != null) {
            removeRoleHolderInternal(role, packageName);
        } else {
            fail("Expected role manager");
        }
    }

    public List<String> getRoleHolder(String roleName) {
        List<String> holders = new ArrayList<>();
        runWithShellPermissionIdentity(() -> {
            List<String> previousHolders = mRoleManager.getRoleHolders(roleName);
            if (previousHolders != null && !previousHolders.isEmpty()) {
                holders.addAll(previousHolders);
            }
        });
        return holders;
    }

    private void addRoleHolder(String roleName, String packageName) throws InterruptedException {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>(1);
        runWithShellPermissionIdentity(() -> {
            mRoleManager.addRoleHolderAsUser(roleName, packageName,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, user, executor,
                    successful -> {
                        if (successful) {
                            q.add(roleName + packageName);
                        } else  {
                            Log.e(TAG, "Add role holder failed.");
                        }
                    });
        });
        String res = q.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(roleName + packageName, res);
    }

    private void removeRoleHolderInternal(String roleName, String packageName)
            throws InterruptedException {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>(1);
        runWithShellPermissionIdentity(() -> {
            mRoleManager.removeRoleHolderAsUser(roleName, packageName, 0, user, executor,
                    successful -> {
                        if (successful) {
                            q.add(roleName + packageName);
                        } else {
                            Log.e(TAG, "Remove role holder failed.");
                        }
                    });
        });
        String res = q.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(roleName + packageName, res);
    }
}
