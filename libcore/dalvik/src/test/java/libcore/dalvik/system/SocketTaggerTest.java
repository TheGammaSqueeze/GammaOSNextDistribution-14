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

package libcore.dalvik.system;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import dalvik.system.SocketTagger;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SocketTaggerTest {

    private static class MySocketTagger extends SocketTagger {

        private FileDescriptor lastTaggedFd = null;
        private FileDescriptor lastUntaggedFd = null;

        @Override
        public void tag(FileDescriptor fd) {
            lastTaggedFd = fd;
        }

        @Override
        public void untag(FileDescriptor fd) {
            lastUntaggedFd = fd;
        }
    }

    @Test
    public void testTagDatagramSocket() throws SocketException {
        MySocketTagger tagger = new MySocketTagger();
        DatagramSocket socket = new DatagramSocket();
        tagger.tag(socket);
        assertSame(socket.getFileDescriptor$(), tagger.lastTaggedFd);

        // Test closed socket
        tagger = new MySocketTagger();
        socket = new DatagramSocket();
        socket.close();
        tagger.tag(socket);
        assertNull(tagger.lastTaggedFd);
    }

    @Test
    public void testUntagDatagramSocket() throws SocketException {
        MySocketTagger tagger = new MySocketTagger();
        DatagramSocket socket = new DatagramSocket();
        tagger.untag(socket);
        assertSame(socket.getFileDescriptor$(), tagger.lastUntaggedFd);

        // Test closed socket
        tagger = new MySocketTagger();
        socket = new DatagramSocket();
        socket.close();
        tagger.untag(socket);
        assertNull(tagger.lastUntaggedFd);
    }

    @Test
    public void testTagSocket() throws IOException {
        MySocketTagger tagger = new MySocketTagger();
        Socket socket = new Socket();
        tagger.tag(socket);
        assertSame(socket.getFileDescriptor$(), tagger.lastTaggedFd);

        // Test closed socket
        tagger = new MySocketTagger();
        socket = new Socket();
        socket.close();
        tagger.tag(socket);
        assertNull(tagger.lastTaggedFd);
    }

    @Test
    public void testUntagSocket() throws IOException {
        MySocketTagger tagger = new MySocketTagger();
        Socket socket = new Socket();
        tagger.untag(socket);
        assertSame(socket.getFileDescriptor$(), tagger.lastUntaggedFd);

        // Test closed socket
        tagger = new MySocketTagger();
        socket = new Socket();
        socket.close();
        tagger.untag(socket);
        assertNull(tagger.lastUntaggedFd);
    }

    @Test
    public void testSet() {
        SocketTagger originalTagger = SocketTagger.get();

        try {
            SocketTagger myTagger = new MySocketTagger();
            SocketTagger.set(myTagger);
            assertSame(myTagger, SocketTagger.get());
        } finally {
            SocketTagger.set(originalTagger);
        }
    }
}
