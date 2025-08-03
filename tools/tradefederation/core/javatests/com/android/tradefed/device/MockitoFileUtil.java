/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.FileListingService;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Helper class for mocking out device file system contents. Mockito verson of MockFileUtil (to be
 * renamed back after all tests referencing MockFileUtil are converted to Mockito).
 */
public class MockitoFileUtil {

    /**
     * Helper method to mock out a remote filesystem contents
     *
     * @param mockDevice the mock {@link ITestDevice}
     * @param rootPath the path to the root
     * @param childNames the child file names of directory to simulate
     * @throws DeviceNotAvailableException
     */
    public static void setMockDirContents(
            ITestDevice mockDevice, String rootPath, String... childNames)
            throws DeviceNotAvailableException {
        IFileEntry rootEntry = mock(IFileEntry.class);
        when(mockDevice.getFileEntry(rootPath)).thenReturn(rootEntry);
        boolean isDir = childNames.length != 0;
        when(rootEntry.isDirectory()).thenReturn(isDir);
        when(rootEntry.getFullEscapedPath()).thenReturn(rootPath);
        when(rootEntry.getName()).thenReturn(rootPath);
        Collection<IFileEntry> mockChildren = new ArrayList<IFileEntry>(childNames.length);
        for (String childName : childNames) {
            IFileEntry childMockEntry = mock(IFileEntry.class);
            when(childMockEntry.getName()).thenReturn(childName);
            String fullPath = rootPath + FileListingService.FILE_SEPARATOR + childName;
            when(childMockEntry.getFullEscapedPath()).thenReturn(fullPath);
            when(childMockEntry.isDirectory()).thenReturn(Boolean.FALSE);
            mockChildren.add(childMockEntry);
        }
        when(rootEntry.getChildren(Mockito.anyBoolean())).thenReturn(mockChildren);
    }

    /**
     * Helper method to mock out a remote nested filesystem contents
     *
     * @param mockDevice the mock {@link ITestDevice}
     * @param rootPath the path to the root
     * @param pathSegments the nested file path to simulate. This method will mock out IFileEntry
     *     objects to simulate a filesystem structure of rootPath/pathSegments
     * @throws DeviceNotAvailableException
     */
    public static void setMockDirPath(
            ITestDevice mockDevice, String rootPath, String... pathSegments)
            throws DeviceNotAvailableException {
        IFileEntry rootEntry = mock(IFileEntry.class);
        when(mockDevice.getFileEntry(rootPath)).thenReturn(rootEntry);
        when(rootEntry.getFullEscapedPath()).thenReturn(rootPath);
        when(rootEntry.getName()).thenReturn(rootPath);
        for (int i = 0; i < pathSegments.length; i++) {
            IFileEntry childMockEntry = mock(IFileEntry.class);
            when(childMockEntry.getName()).thenReturn(pathSegments[i]);
            rootPath = rootPath + FileListingService.FILE_SEPARATOR + pathSegments[i];
            when(childMockEntry.getFullEscapedPath()).thenReturn(rootPath);
            Collection<IFileEntry> childrenResult = new ArrayList<IFileEntry>(1);
            childrenResult.add(childMockEntry);
            when(rootEntry.getChildren(Mockito.anyBoolean())).thenReturn(childrenResult);
            when(rootEntry.isDirectory()).thenReturn(Boolean.TRUE);

            rootEntry = childMockEntry;
        }
        // leaf node - not a directory
        when(rootEntry.isDirectory()).thenReturn(Boolean.FALSE);
    }
}
