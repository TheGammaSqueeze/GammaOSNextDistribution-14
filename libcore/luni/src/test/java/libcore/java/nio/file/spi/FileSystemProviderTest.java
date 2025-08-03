/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.java.nio.file.spi;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FileSystemProviderTest {

    class MockFileSystemProvider extends FileSystemProvider {
        @Override
        public String getScheme() {
            return "mock";
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String,?> env) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path getPath(URI uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path,
                Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir,
                DirectoryStream.Filter<? super Path> filter) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Path path) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSameFile(Path path, Path path2) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(
                Path path, Class<V> type, LinkOption... options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(
                Path path, Class<A> type, LinkOption... options) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String,Object> readAttributes(
                Path path, String attributes, LinkOption... options) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAttribute(Path path, String attribute,
                Object value, LinkOption... options) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testCreateLink() throws Exception {
        MockFileSystemProvider provider = new MockFileSystemProvider();

        Path link = Paths.get("testdir");
        Path existing = Paths.get("testfile");

        try {
            provider.createLink(link, existing);
            fail("Expected UnsupportedOperationException");
        } catch(UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testNewAsynchronousFileChannel() throws Exception {
        MockFileSystemProvider provider = new MockFileSystemProvider();

        Path path = Paths.get("testfile");
        Set<OpenOption> options = new TreeSet<OpenOption>();

        try {
            provider.newAsynchronousFileChannel(path, options, null);
            fail("Expected UnsupportedOperationException");
        } catch(UnsupportedOperationException expected) {
        }
    }
}
