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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class SecurityPolicyDeviceInfo extends DeviceInfo {
    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        File file = getDevice().pullFile("/sys/fs/selinux/policy");
        if (file == null) {
            CLog.e("Failed to pull the policy (got null)");
            return;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            var digestStream = new DigestInputStream(new FileInputStream(file), md);
            digestStream.readAllBytes();
            byte[] digest = md.digest();
            store.addResult("sysfs_sepolicy_hash_sha256", HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            CLog.e("Cannot use sha256");
        }
    }
}
