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

package com.android.sts.common.util;

import com.android.server.os.TombstoneProtos.*;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses tombstones and from a tombstone file or logcat. */
public class TombstoneParser {

    private static final String TOMBSTONE_HEADER =
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***";
    private static final Pattern TOMBSTONE_HEADER_PATTERN =
            Pattern.compile(TOMBSTONE_HEADER.replace("*", "\\*"));
    private static final Pattern NATIVE_CRASH_TIME_PATTERN =
            Pattern.compile("Native Crash TIME: (?<time>\\d+)");
    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("Build fingerprint: '(?<fingerprint>.*)'");
    private static final Pattern REVISION_PATTERN =
            Pattern.compile("Revision: '(?<revision>.*)'\\s*");
    private static final Pattern ABI_PATTERN = Pattern.compile("ABI: '(?<abi>.*)'");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("Timestamp: (?<timestamp>.*)");
    private static final Pattern UPTIME_PATTERN =
            Pattern.compile("Process uptime: (?<uptime>\\d+)s");
    private static final Pattern GET_MAIN_THREAD_FAILURE_PATTERN =
            Pattern.compile("failed to find entry for main thread in tombstone");
    private static final Pattern THREAD_SEPARATOR_PATTERN =
            Pattern.compile("--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---");
    // "    fd %d: %s (%s)"
    private static final Pattern OPEN_FILE_ROW_PATTERN =
            Pattern.compile(
                    " *fd (?<fd>\\d+?): (?<path>.*?) \\((?:(?<unowned>unowned)|(?:owned by"
                            + " (?<owner>\\S+) 0x(?<tag>\\p{XDigit}{1,16})))\\)");
    private static final Pattern SIGNAL_MISSING_PATTERN =
            Pattern.compile("signal information missing");
    // "signal %d (%s), code %d (%s%s), fault addr %s"
    // "signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x7e772b8cfbe0"
    private static final Pattern SIGNAL_PATTERN =
            Pattern.compile(
                    "signal (?<number>\\d+?) \\((?<name>.+?)\\), code (?<code>(?:-)?\\d+?)"
                            + " \\((?<codename>\\S+?)(?: from pid (?<senderpid>\\d+?), uid"
                            + " (?<senderuid>\\d+?))?\\), fault addr"
                            + " (?:0x)?(?<faultaddress>\\p{XDigit}{1,16}|--------)"
                            + "( (?<register>\\(.+\\)))?");
    private static final Pattern CAUSE_PATTERN = Pattern.compile("Cause: (?<cause>.*?)");
    // Be greedy because some abort messages are multiple lines long
    private static final Pattern ABORT_PATTERN =
            Pattern.compile(
                    "^Abort message: '(?<message>.*)'$", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern DEALLOC_PATTERN =
            Pattern.compile("deallocated by thread (?<tid>\\d+):");
    private static final Pattern ALLOC_PATTERN =
            Pattern.compile("allocated by thread (?<tid>\\d+):");
    private static final Pattern NO_MEMORY_MAPS_PATTERN = Pattern.compile("No memory maps found");
    // "memory map (%d %s):" + ("\n--->Fault address falls at %s before any mapped regions" | "
    // (fault address prefixed with --->)")
    private static final Pattern MEMORY_MAP_HEADER_PATTERN =
            Pattern.compile(
                    "memory map \\((?<count>\\d+?) entr(?:y|ies)\\):(?: \\(fault address prefixed"
                            + " with --->\\))?");
    private static final Pattern MEMORY_MAP_FAULT_ADDRESS_BETWEEN_PATTERN =
            Pattern.compile(
                    "--->Fault address falls at"
                        + " (?<faultaddresshigh>\\p{XDigit}{4,8})'(?<faultaddresslow>\\p{XDigit}{4,8})"
                        + " between mapped regions");
    // "    " + ("--->")? + "%s-%s" + %s%s%s" + "  %8" PRIx64 "  %8" PRIx64" + ("  %s")? + ("
    // (BuildId: %s)")? + (" (load bias 0x%" PRIx64 ")")?
    private static final Pattern MEMORY_MAP_LINE_PATTERN =
            Pattern.compile(
                    " *(?:--->)?(?<beginaddresshigh>\\p{XDigit}{4,8})'(?<beginaddresslow>\\p{XDigit}{4,8})-(?<endaddresshigh>\\p{XDigit}{4,8})'(?<endaddresslow>\\p{XDigit}{4,8})"
                        + " (?<read>\\S)(?<write>\\S)(?<execute>\\S)  "
                        + " {0,7}(?<offset>\\p{XDigit}{1,8})   {0,7}(?<length>\\p{XDigit}{1,8})(?:"
                        + "  (?<mappingname>.+?))?(?: \\(BuildId: (?<buildid>\\d+)\\))?(?: \\(load"
                        + " bias 0x(?<loadbias>\\p{XDigit}+)\\))?");
    private static final Pattern MEMORY_MAP_FAULT_ADDRESS_AFTER_PATTERN =
            Pattern.compile(
                    "--->Fault address falls at"
                        + " (?<faultaddresshigh>\\p{XDigit}{4,8})'(?<faultaddresslow>\\p{XDigit}{4,8})"
                        + " after any mapped regions");
    private static final Pattern CMD_LINE_PATTERN = Pattern.compile("Cmdline: (?<cmd>.*)");
    private static final Pattern THREAD_HEADER_1_PATTERN =
            Pattern.compile(
                    "pid: (?<pid>\\d+), tid: (?<tid>\\d+), name: (?<threadname>.+?)  >>>"
                            + " (?<processname>.+?) <<<");
    private static final Pattern THREAD_HEADER_2_PATTERN = Pattern.compile("uid: (?<uid>\\d+)\\s*");
    private static final Pattern TAGGED_ADDR_CTRL_PATTERN =
            Pattern.compile(
                    "tagged_addr_ctrl: (?<taggedaddrctrl>\\p{XDigit}{16})(?<description>.+)?");
    // TODO: finish description
    private static final Pattern PAC_ENABLED_KEYS_PATTERN =
            Pattern.compile(
                    "pac_enabled_keys: (?<pacenabledkeys>\\p{XDigit}{16})(?<description>.+)?");
    private static final Pattern REGISTER_ROW_PATTERN = Pattern.compile("  .*");
    private static final Pattern BACKTRACE_HEADER_PATTERN = Pattern.compile("backtrace:");
    private static final Pattern BACKTRACE_NOTE_PATTERN = Pattern.compile(" *NOTE: (?<note>.*)");
    // "      #05 pc 000000000004faf6  /apex/com.android.runtime/lib64/bionic/libc.so
    // (__libc_init+86) (BuildId: 284d864ffe434d73dc722b84a1d3d9ca)"
    private static final Pattern BACKTRACE_PATTERN =
            Pattern.compile(
                    " *#(?<index>\\d{2}) pc (?<programcounter>\\p{XDigit}{8,16}) "
                        + " (?<filename>.+?)(?:"
                        + " \\((?<functionname>.*?)\\+(?<functionoffset>\\d+)\\))?(?: \\(BuildId:"
                        + " (?<buildid>.*?)\\))?");
    private static final Pattern MEMORY_NEAR_PATTERN =
            Pattern.compile("memory near (?<registername>\\S+?)(?: \\((?<mappingname>\\S+?)\\))?:");
    // "    0000006f0bb2a8a0 6f735c65646f635c 6d61675c65637275  \code\source\gam"
    private static final Pattern MEMORY_DUMP_ROW_PATTERN =
            Pattern.compile(
                    " *(?<address>\\p{XDigit}{8,16}) (?<memory1>(?:\\p{XDigit}{8,16}|-{8,16}))(?:"
                            + " (?<memory2>(?:\\p{XDigit}{8,16}|-{8,16})))?(?:"
                            + " (?<memory3>(?:\\p{XDigit}{8,16}|-{8,16})))?(?:"
                            + " (?<memory4>(?:\\p{XDigit}{8,16}|-{8,16})))?  (?<ascii>.{1,16})");
    private static final Pattern MEMORY_TAG_PATTERN =
            Pattern.compile(
                    "Memory tags around the fault address"
                            + " \\(0x(?<faultaddress>\\p{XDigit}{8,16})\\), one tag per"
                            + " (?<taggranulesize>) bytes:");
    private static final Pattern MEMORY_TAG_ROW_PATTERN =
            Pattern.compile("    (?:=>|  )0x(?<address>\\p{XDigit}{8,16}):(?<tags>.*)");
    private static final Pattern LOG_PATTERN =
            Pattern.compile("---------(?<istail> tail end of) log (?<buffername>.*)");
    private static final Pattern LOG_LINE_PATTERN =
            Pattern.compile(
                    "(?<timestamp>.+?) {1,5}(?<pid>\\d+) {1,5}(?<tid>\\d+) (?<priority>\\S)"
                            + " (?<tag>\\S*) {0,8}: (?<message>.*)");

    private static final Pattern CURRENT_BACKTRACE_BLOB_PATTERN =
            Pattern.compile(
                    String.format(
                            "^%s\n(?:%s\n)*(?:%s\n)*",
                            BACKTRACE_HEADER_PATTERN.pattern(),
                            BACKTRACE_NOTE_PATTERN.pattern(),
                            BACKTRACE_PATTERN.pattern()),
                    Pattern.MULTILINE);
    private static final Pattern ALLOC_BACKTRACE_BLOB_PATTERN =
            Pattern.compile(
                    String.format(
                            "^%s\n(?:%s\n)*", ALLOC_PATTERN.pattern(), BACKTRACE_PATTERN.pattern()),
                    Pattern.MULTILINE);
    private static final Pattern DEALLOC_BACKTRACE_BLOB_PATTERN =
            Pattern.compile(
                    String.format(
                            "^%s\n(?:%s\n)*",
                            DEALLOC_PATTERN.pattern(), BACKTRACE_PATTERN.pattern()),
                    Pattern.MULTILINE);

    /** Parse a logcat snippet and build a list of tombstones */
    public static final List<Tombstone> parseLogcat(String logcat) {
        String[] potentialTombstones = splitPattern(TOMBSTONE_HEADER_PATTERN).split(logcat);

        List<Tombstone> tombstones = new ArrayList<>();
        for (String potentialTombstone : potentialTombstones) {
            Tombstone.Builder tombstoneBuilder = Tombstone.newBuilder();
            List<String> lines = lines(potentialTombstone);
            if (lines.isEmpty()) {
                continue;
            }
            if (!lines.get(0).contains(TOMBSTONE_HEADER)) {
                continue;
            }
            if (NATIVE_CRASH_TIME_PATTERN.matcher(lines.get(1)).find()) {
                CLog.d("ignoring crash time");
                continue;
            }

            String tombstoneBlob =
                    lines.stream()
                            .filter(line -> line.contains("DEBUG   :"))
                            .map(
                                    line -> {
                                        // logcat removes trailing space after ":" in the case of
                                        // empty lines
                                        String[] split = line.split("DEBUG   :(?: )?", 2);
                                        return split.length == 2 ? split[1] : "<empty>";
                                    })
                            .collect(
                                    StringBuilder::new,
                                    (sb, line) -> {
                                        sb.append(line);
                                        sb.append('\n');
                                    },
                                    StringBuilder::append)
                            .toString();

            if (!parseTombstone(tombstoneBlob, tombstoneBuilder)) {
                CLog.w("parsing tombstone failed: \n" + tombstoneBlob);
            }
            Tombstone tombstone = tombstoneBuilder.build();
            tombstones.add(tombstone);
        }
        return tombstones;
    }

    public static boolean parseTombstone(String tombstoneBlob, Tombstone.Builder tombstoneBuilder) {
        // get build or bail
        // get revision or bail
        // get ABI or bail
        // get timestamp or bail
        // get uptime or bail
        // try "failed to find entry for main thread..." and add to notes(?) (if present, return)
        //
        // get main thread:
        //
        // get logs:
        // try ----- tail end of log %x
        // else log %x
        // loop
        //   try jfeiowajfklewjaoi
        //
        // loop
        //   try --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
        //   get thread
        // try ""
        // ifp open files
        // ifp loop
        //   "    fd ..."
        // get logs

        String[] threadBlobs = THREAD_SEPARATOR_PATTERN.split(tombstoneBlob);
        String headerAndMainThreadBlob = threadBlobs[0];
        List<String> headerAndMainThreadLines = lines(headerAndMainThreadBlob);

        // get fingerprint
        if (!matchLine(
                headerAndMainThreadLines.iterator(),
                FINGERPRINT_PATTERN,
                m -> {
                    String fingerprint = m.group("fingerprint");
                    CLog.i(fingerprint);
                    tombstoneBuilder.setBuildFingerprint(fingerprint);
                })) {
            CLog.w("fingerprint failed");
            return false;
        }

        // get revision
        if (!matchLine(
                headerAndMainThreadLines.iterator(),
                REVISION_PATTERN,
                m -> {
                    tombstoneBuilder.setRevision(m.group("revision"));
                })) {
            CLog.w("revision failed");
            return false;
        }

        // get ABI
        if (!matchLine(
                headerAndMainThreadLines.iterator(),
                ABI_PATTERN,
                m -> {
                    final String abi = m.group("abi");
                    Architecture arch = null;
                    switch (abi) {
                        case "arm":
                            arch = Architecture.ARM32;
                            break;
                        case "arm64":
                            arch = Architecture.ARM64;
                            break;
                        case "riscv64":
                            arch = Architecture.RISCV64;
                            break;
                        case "x86":
                            arch = Architecture.X86;
                            break;
                        case "x86_64":
                            arch = Architecture.X86_64;
                            break;
                        default:
                            CLog.i("unknown arch");
                            return;
                    }
                    CLog.d("set arch to: " + arch);
                    tombstoneBuilder.setArch(arch);
                })) {
            CLog.w("abi failed");
            return false;
        }

        // get timestamp
        matchLine(
                headerAndMainThreadLines.iterator(),
                TIMESTAMP_PATTERN,
                m -> {
                    tombstoneBuilder.setTimestamp(m.group("timestamp"));
                });

        // get process uptime
        matchLine(
                headerAndMainThreadLines.iterator(),
                UPTIME_PATTERN,
                m -> {
                    String uptime = m.group("uptime");
                    tombstoneBuilder.setProcessUptime(Integer.valueOf(uptime));
                });

        // try main thread get failure note
        if (matchLine(headerAndMainThreadLines.iterator(), GET_MAIN_THREAD_FAILURE_PATTERN, null)) {
            // tombstoned couldn't get the main thread info, so that's all we get
            return true;
        }

        // get main thread
        if (!parseMainThread(headerAndMainThreadBlob, tombstoneBuilder)) {
            CLog.w("main thread failed");
            return false;
        }

        // get logs
        if (!parseLogs(headerAndMainThreadBlob, tombstoneBuilder)) {
            CLog.w("logs failed");
            return false;
        }

        // get threads
        for (int i = 1; i < threadBlobs.length; i++) {
            String threadBlob = threadBlobs[i];
            com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder =
                    com.android.server.os.TombstoneProtos.Thread.newBuilder();
            if (!parseThread(threadBlob, tombstoneBuilder, threadBuilder)) {
                CLog.w("thread failed");
                return false;
            }
            tombstoneBuilder.putThreads(threadBuilder.getId(), threadBuilder.build());
        }

        // get end of blob
        String tailBlob = threadBlobs[threadBlobs.length - 1];
        List<String> tailLines = lines(tailBlob);

        // get open files
        matchLines(
                tailLines.iterator(),
                OPEN_FILE_ROW_PATTERN,
                m -> {
                    FD.Builder fdBuilder =
                            FD.newBuilder()
                                    .setFd(Integer.valueOf(m.group("fd")))
                                    .setPath(m.group("path"));
                    String owner = m.group("owner");
                    String tag = m.group("tag");
                    if (owner != null && tag != null) {
                        fdBuilder.setOwner(owner).setTag(parsePointer(tag));
                    } else {
                        fdBuilder.setOwner("unowned");
                    }
                    tombstoneBuilder.addOpenFds(fdBuilder.build());
                });

        if (!parseLogs(tailBlob, tombstoneBuilder)) {
            CLog.w("logs failed");
            return false;
        }

        return true;
    }

    private static boolean parseMainThread(
            String headerAndMainThreadBlob, Tombstone.Builder tombstoneBuilder) {
        com.android.server.os.TombstoneProtos.Thread.Builder mainThreadBuilder =
                com.android.server.os.TombstoneProtos.Thread.newBuilder();

        List<String> headerAndMainThreadLines = lines(headerAndMainThreadBlob);
        String[] causeBlobs = splitPattern(CAUSE_PATTERN).split(headerAndMainThreadBlob);
        String headerBlob = causeBlobs[0];
        List<String> headerLines = lines(headerBlob);
        String tailBlob = causeBlobs[causeBlobs.length - 1];
        List<String> tailLines = lines(tailBlob);

        try {
            if (!parseThreadHeader(
                    headerLines, tombstoneBuilder, mainThreadBuilder, /* isMainThread */ true)) {
                CLog.w("main thread get header failed");
                return false;
            }

            // get signal or no signal
            boolean matchedSignal = false;
            matchedSignal |= matchLine(headerLines.iterator(), SIGNAL_MISSING_PATTERN, null);
            matchedSignal |=
                    matchLine(
                            headerLines.iterator(),
                            SIGNAL_PATTERN,
                            m -> {
                                Signal.Builder signalBuilder =
                                        Signal.newBuilder()
                                                .setNumber(Integer.valueOf(m.group("number")))
                                                .setName(m.group("name"))
                                                .setCode(Integer.valueOf(m.group("code")))
                                                .setCodeName(m.group("codename"));

                                String faultAddress = m.group("faultaddress");
                                if (!faultAddress.equals("--------")) {
                                    signalBuilder
                                            .setHasFaultAddress(true)
                                            .setFaultAddress(parsePointer(faultAddress));
                                } else {
                                    signalBuilder.setHasFaultAddress(false);
                                }
                                String senderUid = m.group("senderuid");
                                String senderPid = m.group("senderpid");
                                if (senderUid != null && senderPid != null) {
                                    signalBuilder
                                            .setSenderUid(Integer.valueOf(senderUid))
                                            .setSenderPid(Integer.valueOf(senderPid));
                                }
                                // TODO: add fault-adjacent metadata, which we don't get until the
                                // tag dump :(
                                tombstoneBuilder.setSignalInfo(signalBuilder.build());
                            });
            if (!matchedSignal) {
                // must match one or the other
                CLog.w("couldn't match signal messages");
                return false;
            }

            // single cause pattern goes here, so the "header blob" won't contain stuff after

            // get abort if present
            {
                Matcher m = ABORT_PATTERN.matcher(headerAndMainThreadBlob);
                if (m.find()) {
                    tombstoneBuilder.setAbortMessage(m.group("message"));
                }
            }

            if (!parseThreadRegisters(headerAndMainThreadLines, mainThreadBuilder)) {
                CLog.w("main thread get thread registers failed");
                return false;
            }

            if (!parseThreadBacktrace(headerAndMainThreadBlob, mainThreadBuilder)) {
                CLog.w("main thread get thread backtrace failed");
                return false;
            }

            // get causes
            for (int i = 1; i < causeBlobs.length; i++) {
                String causeBlob = causeBlobs[i];
                List<String> causeLines = lines(causeBlob);
                Cause.Builder causeBuilder = Cause.newBuilder();
                if (!matchLine(
                        causeLines.iterator(),
                        CAUSE_PATTERN,
                        m -> {
                            // must delay adding cause because the memory error is printed later
                            CLog.d("cause message matched");
                            causeBuilder.setHumanReadable(m.group("cause"));
                        })) {
                    // not a cause
                    continue;
                }

                boolean hasMemoryError = false;
                MemoryError.Builder memoryErrorBuilder = MemoryError.newBuilder();
                HeapObject.Builder heapObjectBuilder = HeapObject.newBuilder();

                {
                    Matcher deallocBlobMatcher = DEALLOC_BACKTRACE_BLOB_PATTERN.matcher(causeBlob);
                    if (deallocBlobMatcher.find()) {
                        List<String> deallocLines = lines(deallocBlobMatcher.group(0));
                        hasMemoryError = true;
                        CLog.d("dealloc matched");
                        List<BacktraceFrame> backtraceFrames = new ArrayList<>();
                        if (!parseBacktrace(deallocLines, backtraceFrames)) {
                            return false;
                        }
                        heapObjectBuilder.addAllDeallocationBacktrace(backtraceFrames);
                    }
                }

                {
                    Matcher allocBlobMatcher = ALLOC_BACKTRACE_BLOB_PATTERN.matcher(causeBlob);
                    if (allocBlobMatcher.find()) {
                        List<String> allocLines = lines(allocBlobMatcher.group(0));
                        hasMemoryError = true;
                        CLog.d("alloc matched");
                        List<BacktraceFrame> backtraceFrames = new ArrayList<>();
                        if (!parseBacktrace(allocLines, backtraceFrames)) {
                            return false;
                        }
                        heapObjectBuilder.addAllAllocationBacktrace(backtraceFrames);
                    }
                }

                if (hasMemoryError) {
                    memoryErrorBuilder.setHeap(heapObjectBuilder.build());
                    causeBuilder.setMemoryError(memoryErrorBuilder.build());
                } else {
                    CLog.d("no memory errors");
                }
                tombstoneBuilder.addCauses(causeBuilder.build());
            }

            if (!parseTagDump(tailLines, tombstoneBuilder)) {
                CLog.w("tag dump failed");
                return false;
            }

            if (!parseThreadMemoryDump(tailBlob, mainThreadBuilder)) {
                CLog.w("memory dump failed");
                return false;
            }
        } finally {
            tombstoneBuilder.putThreads(mainThreadBuilder.getId(), mainThreadBuilder.build());
        }

        // check if no memory maps
        if (matchLine(tailLines.iterator(), NO_MEMORY_MAPS_PATTERN, null)) {
            // no more lines follow
            return true;
        }

        String[] memoryMapBlobs = splitPattern(MEMORY_MAP_HEADER_PATTERN).split(tailBlob);

        for (int i = 1; i < memoryMapBlobs.length; i++) {
            MemoryMapping.Builder memoryMappingBuilder = MemoryMapping.newBuilder();

            String memoryMapBlob = memoryMapBlobs[i];
            List<String> memoryMapLines = lines(memoryMapBlob);

            matchLine(
                    memoryMapLines.iterator(),
                    MEMORY_MAP_FAULT_ADDRESS_BETWEEN_PATTERN,
                    m -> {
                        CLog.d("got fault address between");
                        // TODO: put fault address in memoryMappingBuilder
                    });

            matchLine(
                    memoryMapLines.iterator(),
                    MEMORY_MAP_HEADER_PATTERN,
                    m -> {
                        int count = Integer.valueOf(m.group("count"));
                        CLog.d("got memory map entries count: " + count);
                        matchLines(
                                memoryMapLines.iterator(),
                                MEMORY_MAP_LINE_PATTERN,
                                mLine -> {
                                    // TODO: parse
                                    tombstoneBuilder.addMemoryMappings(
                                            memoryMappingBuilder.build());
                                });
                    });

            matchLine(
                    memoryMapLines.iterator(),
                    MEMORY_MAP_FAULT_ADDRESS_AFTER_PATTERN,
                    m -> {
                        CLog.d("got fault address after");
                        // TODO: put fault address in memoryMappingBuilder
                    });
        }

        return true;
    }

    private static boolean parseThread(
            String blob,
            Tombstone.Builder tombstoneBuilder,
            com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder) {
        List<String> lines = lines(blob);
        if (!parseThreadHeader(lines, tombstoneBuilder, threadBuilder, /* isMainThread */ false)) {
            return false;
        }
        if (!parseThreadRegisters(lines, threadBuilder)) {
            return false;
        }
        if (!parseThreadBacktrace(blob, threadBuilder)) {
            return false;
        }
        if (!parseThreadMemoryDump(blob, threadBuilder)) {
            return false;
        }
        return true;
    }

    private static boolean parseThreadHeader(
            List<String> lines,
            Tombstone.Builder tombstoneBuilder,
            com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder,
            boolean isMainThread) {
        matchLine(
                lines.iterator(),
                CMD_LINE_PATTERN,
                m -> {
                    if (isMainThread) {
                        tombstoneBuilder.addAllCommandLine(
                                Arrays.asList(m.group("cmd").split(" ")));
                    }
                });
        matchLine(
                lines.iterator(),
                THREAD_HEADER_1_PATTERN,
                m -> {
                    int tid = Integer.valueOf(m.group("tid"));
                    if (isMainThread) {
                        tombstoneBuilder.setPid(Integer.valueOf(m.group("pid"))).setTid(tid);
                        if (tombstoneBuilder.getCommandLineList().isEmpty()) {
                            tombstoneBuilder.addCommandLine(m.group("processname"));
                        }
                    }
                    threadBuilder.setId(tid).setName(m.group("threadname"));
                });
        matchLine(
                lines.iterator(),
                THREAD_HEADER_2_PATTERN,
                m -> {
                    if (isMainThread) {
                        tombstoneBuilder.setUid(Integer.valueOf(m.group("uid")));
                    }
                });

        matchLine(
                lines.iterator(),
                TAGGED_ADDR_CTRL_PATTERN,
                m -> {
                    // TODO: add to tombstone
                });

        matchLine(
                lines.iterator(),
                PAC_ENABLED_KEYS_PATTERN,
                m -> {
                    // TODO: add to tombstone
                });

        return true;
    }

    private static boolean parseThreadRegisters(
            List<String> lines,
            com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder) {
        return matchLines(
                        lines.iterator(),
                        REGISTER_ROW_PATTERN,
                        m -> {
                            // TODO: parse line and add to tombstone
                        })
                > 0;
    }

    private static boolean parseThreadBacktrace(
            String blob, com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder) {
        Matcher threadBacktraceMatcher = CURRENT_BACKTRACE_BLOB_PATTERN.matcher(blob);
        if (!threadBacktraceMatcher.find()) {
            CLog.i("didn't match backtrace blob");
            return true; // no backtrace
        }
        String threadBacktraceBlob = threadBacktraceMatcher.group(0); // entire match
        List<String> lines = lines(threadBacktraceBlob);

        matchLines(
                lines.iterator(),
                BACKTRACE_NOTE_PATTERN,
                m -> {
                    threadBuilder.addBacktraceNote(m.group("note"));
                });

        List<BacktraceFrame> backtraceFrames = new ArrayList<>();
        if (!parseBacktrace(lines, backtraceFrames)) {
            return false;
        }
        threadBuilder.addAllCurrentBacktrace(backtraceFrames);
        return true;
    }

    private static boolean parseBacktrace(
            List<String> lines, List<BacktraceFrame> backtraceFrames) {
        matchLines(
                lines.iterator(),
                BACKTRACE_PATTERN,
                m -> {
                    BacktraceFrame.Builder backtraceFrameBuilder =
                            BacktraceFrame.newBuilder()
                                    .setRelPc(parsePointer(m.group("programcounter")))
                                    .setFileName(m.group("filename"));
                    String functionName = m.group("functionname");
                    String functionOffset = m.group("functionoffset");
                    if (functionName != null && functionOffset != null) {
                        backtraceFrameBuilder
                                .setFunctionName(functionName)
                                .setFunctionOffset(Long.valueOf(functionOffset));
                    }
                    String buildId = m.group("buildid");
                    if (buildId != null) {
                        backtraceFrameBuilder.setBuildId(buildId);
                    }
                    backtraceFrames.add(backtraceFrameBuilder.build());
                });

        return true;
    }

    private static boolean parseThreadMemoryDump(
            String blob, com.android.server.os.TombstoneProtos.Thread.Builder threadBuilder) {

        String[] memoryDumpBlobs = splitPattern(MEMORY_NEAR_PATTERN).split(blob);
        String headerBlob = memoryDumpBlobs[0];
        List<String> headerLines = lines(headerBlob);

        for (int i = 1; i < memoryDumpBlobs.length; i++) {
            String memoryDumpBlob = memoryDumpBlobs[i];
            List<String> lines = lines(memoryDumpBlob);

            matchLine(
                    lines.iterator(),
                    MEMORY_NEAR_PATTERN,
                    m -> {
                        // TODO: add memory dump to tombstone
                    });

            matchLines(
                    lines.iterator(),
                    MEMORY_DUMP_ROW_PATTERN,
                    m -> {
                        // TODO: add memory dump to tombstone
                    });
        }

        return true;
    }

    private static boolean parseTagDump(List<String> lines, Tombstone.Builder tombstoneBuilder) {

        matchLine(
                lines.iterator(),
                MEMORY_TAG_PATTERN,
                m -> {
                    // TODO: add memory tag to tombstone builder
                });

        matchLines(
                lines.iterator(),
                MEMORY_TAG_ROW_PATTERN,
                m -> {
                    // TODO: add memory tag to tombstone builder
                });

        return true;
    }

    private static boolean parseLogs(String blob, Tombstone.Builder tombstoneBuilder) {

        String[] logBlobs = LOG_PATTERN.split(blob);
        for (int i = 1; i < logBlobs.length; i++) {
            List<String> lines = lines(logBlobs[i]);

            matchLine(
                    lines.iterator(),
                    LOG_PATTERN,
                    m -> {
                        CLog.i("found logs");
                        // TODO: determine if we're the tail end of the logs
                        // TODO: add logs to tombstone builder
                    });

            matchLines(
                    lines.iterator(),
                    LOG_LINE_PATTERN,
                    m -> {
                        // TODO: add logs to tombstone builder
                    });
        }

        return true;
    }

    private static boolean matchLine(
            Iterator<String> lines, Pattern pattern, Consumer<Matcher> onMatch) {
        return matchLines(lines, pattern, 1, onMatch) == 1;
    }

    private static int matchLines(
            Iterator<String> lines, Pattern pattern, Consumer<Matcher> onMatch) {
        return matchLines(lines, pattern, -1, onMatch);
    }

    private static int matchLines(
            Iterator<String> lines, Pattern pattern, int times, Consumer<Matcher> onMatch) {
        int matches = 0;
        while (lines.hasNext() && (times < 0 || matches < times)) {
            String line = lines.next();
            Matcher m = pattern.matcher(line);
            if (m.matches()) {
                matches++;
                if (onMatch != null) {
                    onMatch.accept(m);
                }
            }
        }
        return matches;
    }

    private static List<String> lines(String s) {
        return Arrays.asList(s.split("\\R"));
    }

    private static Pattern splitPattern(Pattern pattern) {
        return Pattern.compile(String.format("((?=^.*%s))", pattern.pattern()), Pattern.MULTILINE);
    }

    public static long parsePointer(String pointerString) {
        return Long.parseUnsignedLong(pointerString, 16);
    }
}
