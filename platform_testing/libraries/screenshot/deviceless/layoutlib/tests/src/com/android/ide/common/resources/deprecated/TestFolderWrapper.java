/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ide.common.resources.deprecated;

import com.android.io.FolderWrapper;
import com.android.io.IAbstractResource;

import java.io.File;

public class TestFolderWrapper extends FolderWrapper {

    public TestFolderWrapper(String pathname) {
        super(pathname);
    }

    public TestFolderWrapper(File file) {
        super(file.getAbsolutePath());
    }

    public IAbstractResource[] listMembers() {
        File[] files = listFiles();
        final int count = files == null ? 0 : files.length;
        IAbstractResource[] afiles = new IAbstractResource[count];

        if (files != null) {
            for (int i = 0 ; i < count ; i++) {
                File f = files[i];
                if (f.isFile()) {
                    afiles[i] = new TestFileWrapper(f);
                } else if (f.isDirectory()) {
                    afiles[i] = new TestFolderWrapper(f);
                }
            }
        }

        return afiles;
    }

    public TestFolderWrapper getFolder(String name) {
        return new TestFolderWrapper(new File(this, name));
    }
}
