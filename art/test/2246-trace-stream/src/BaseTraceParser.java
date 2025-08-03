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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

abstract class BaseTraceParser {
    public static final int MAGIC_NUMBER = 0x574f4c53;
    public static final int DUAL_CLOCK_VERSION = 3;
    public static final int WALL_CLOCK_VERSION = 2;
    public static final int STREAMING_DUAL_CLOCK_VERSION = 0xF3;
    public static final int STREAMING_WALL_CLOCK_VERSION = 0xF2;
    public static final String START_SECTION_ID = "*";
    public static final String METHODS_SECTION_ID = "*methods";
    public static final String THREADS_SECTION_ID = "*threads";
    public static final String END_SECTION_ID = "*end";

    public void InitializeParser(File file) throws IOException {
        dataStream = new DataInputStream(new FileInputStream(file));
        methodIdMap = new HashMap<Integer, String>();
        threadIdMap = new HashMap<Integer, String>();
        nestingLevelMap = new HashMap<Integer, Integer>();
        threadEventsMap = new HashMap<String, String>();
        threadTimestamp1Map = new HashMap<Integer, Integer>();
        threadTimestamp2Map = new HashMap<Integer, Integer>();
    }

    public void closeFile() throws IOException {
        dataStream.close();
    }

    public String readString(int numBytes) throws IOException {
        byte[] buffer = new byte[numBytes];
        dataStream.readFully(buffer);
        return new String(buffer, StandardCharsets.UTF_8);
    }

    public String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        char lineSeparator = '\n';
        char c = (char)dataStream.readUnsignedByte();
        while ( c != lineSeparator) {
            sb.append(c);
            c = (char)dataStream.readUnsignedByte();
        }
        return sb.toString();
    }

    public int readNumber(int numBytes) throws IOException {
        int number = 0;
        for (int i = 0; i < numBytes; i++) {
            number += dataStream.readUnsignedByte() << (i * 8);
        }
        return number;
    }

    public void validateTraceHeader(int expectedVersion) throws Exception {
        // Read 4-byte magicNumber.
        int magicNumber = readNumber(4);
        if (magicNumber != MAGIC_NUMBER) {
            throw new Exception("Magic number doesn't match. Expected "
                    + Integer.toHexString(MAGIC_NUMBER) + " Got "
                    + Integer.toHexString(magicNumber));
        }
        // Read 2-byte version.
        int version = readNumber(2);
        if (version != expectedVersion) {
            throw new Exception(
                    "Unexpected version. Expected " + expectedVersion + " Got " + version);
        }
        traceFormatVersion = version & 0xF;
        // Read 2-byte headerLength length.
        int headerLength = readNumber(2);
        // Read 8-byte starting time - Ignore timestamps since they are not deterministic.
        dataStream.skipBytes(8);
        // 4 byte magicNumber + 2 byte version + 2 byte offset + 8 byte timestamp.
        int numBytesRead = 16;
        if (version >= DUAL_CLOCK_VERSION) {
            // Read 2-byte record size.
            // TODO(mythria): Check why this is needed. We can derive recordSize from version. Not
            // sure why this is needed.
            recordSize = readNumber(2);
            numBytesRead += 2;
        }
        // Skip any padding.
        if (headerLength > numBytesRead) {
            dataStream.skipBytes(headerLength - numBytesRead);
        }
    }

    public int GetThreadID() throws IOException {
        // Read 2-byte thread-id. On host thread-ids can be greater than 16-bit but it is truncated
        // to 16-bits in the trace.
        int threadId = readNumber(2);
        return threadId;
    }

    public int GetEntryHeader() throws IOException {
        // Read 1-byte header type
        return readNumber(1);
    }

    public void ProcessMethodInfoEntry() throws IOException {
        // Read 2-byte method info size
        int headerLength = readNumber(2);
        // Read header size data.
        String methodInfo = readString(headerLength);
        String[] tokens = methodInfo.split("\t", 2);
        // Get methodId and record methodId -> methodName map.
        int methodId = Integer.decode(tokens[0]);
        String methodLine = tokens[1].replace('\t', ' ');
        methodLine = methodLine.substring(0, methodLine.length() - 1);
        methodIdMap.put(methodId, methodLine);
    }

    public void ProcessThreadInfoEntry() throws IOException {
        // Read 2-byte thread id
        int threadId = readNumber(2);
        // Read 2-byte thread info size
        int headerLength = readNumber(2);
        // Read header size data.
        String threadInfo = readString(headerLength);
        threadIdMap.put(threadId, threadInfo);
    }

    public boolean ShouldCheckThread(int threadId, String threadName) throws Exception {
        if (!threadIdMap.containsKey(threadId)) {
          System.out.println("no threadId -> name  mapping for thread " + threadId);
          // TODO(b/279547877): Ideally we should throw here, since it isn't expected. Just
          // continuing to get more logs from the bots to see what's happening here. The
          // test will fail anyway because the expected output will be different.
          return true;
        }

        return threadIdMap.get(threadId).equals(threadName);
    }

    public String eventTypeToString(int eventType, int threadId) {
        if (!nestingLevelMap.containsKey(threadId)) {
            nestingLevelMap.put(threadId, 0);
        }

        int nestingLevel = nestingLevelMap.get(threadId);
        String str = "";
        for (int i = 0; i < nestingLevel; i++) {
            str += ".";
        }
        switch (eventType) {
            case 0:
                nestingLevel++;
                str += ".>>";
                break;
            case 1:
                nestingLevel--;
                str += "<<";
                break;
            case 2:
                nestingLevel--;
                str += "<<E";
                break;
            default:
                str += "??";
        }
        nestingLevelMap.put(threadId, nestingLevel);
        return str;
    }

    public void CheckTimestamp(int timestamp, int threadId,
            HashMap<Integer, Integer> threadTimestampMap) throws Exception {
        if (threadTimestampMap.containsKey(threadId)) {
            int oldTimestamp = threadTimestampMap.get(threadId);
            if (timestamp < oldTimestamp) {
                throw new Exception("timestamps are not increasing current: " + timestamp
                        + "  earlier: " + oldTimestamp);
            }
        }
        threadTimestampMap.put(threadId, timestamp);
    }

    public String ProcessEventEntry(int threadId) throws IOException, Exception {
        // Read 4-byte method value
        int methodAndEvent = readNumber(4);
        int methodId = methodAndEvent & ~0x3;
        int eventType = methodAndEvent & 0x3;

        String str = eventTypeToString(eventType, threadId) + " " + threadIdMap.get(threadId)
                + " " + methodIdMap.get(methodId);
        // Depending on the version skip either one or two timestamps.
        int timestamp1 = readNumber(4);
        CheckTimestamp(timestamp1, threadId, threadTimestamp1Map);
        if (traceFormatVersion != 2) {
            // Read second timestamp
            int timestamp2 = readNumber(4);
            CheckTimestamp(timestamp2, threadId, threadTimestamp2Map);
        }
        return str;
    }

    public void UpdateThreadEvents(int threadId, String entry) {
        String threadName = threadIdMap.get(threadId);
        if (!threadEventsMap.containsKey(threadName)) {
            threadEventsMap.put(threadName, entry);
            return;
        }
        threadEventsMap.put(threadName, threadEventsMap.get(threadName) + "\n" + entry);
    }

    public abstract void CheckTraceFileFormat(File traceFile,
        int expectedVersion, String threadName) throws Exception;

    DataInputStream dataStream;
    HashMap<Integer, String> methodIdMap;
    HashMap<Integer, String> threadIdMap;
    HashMap<Integer, Integer> nestingLevelMap;
    HashMap<String, String> threadEventsMap;
    HashMap<Integer, Integer> threadTimestamp1Map;
    HashMap<Integer, Integer> threadTimestamp2Map;
    int recordSize = 0;
    int traceFormatVersion = 0;
}
