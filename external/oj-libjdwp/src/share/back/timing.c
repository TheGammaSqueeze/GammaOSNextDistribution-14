/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "timing.h"

#include "error_messages.h"
#include "JDWP.h"
#include "outStream.h"
#include "util.h"

// ANDROID-CHANGED: This whole file

// This system stores cmd processing timing and sends them to the debugger
// to generate profiling stats.

typedef struct Timing {
    jlong start_ns;
    jlong duration_ns;
    jint id;
    jint cmd_set;
    jint cmd;
} Timing;

static const jint MAX_TIMINGS = 500;
static Timing timings[MAX_TIMINGS];
static jint numTimings;

void timings_startCmd(jint id, jint cmd_set, jint cmd) {
  timings[numTimings].id = id;
  timings[numTimings].cmd_set = cmd_set;
  timings[numTimings].cmd = cmd;
  timings[numTimings].start_ns = nsTime();
}

void timings_endCmd() {
  timings[numTimings].duration_ns = nsTime() - timings[numTimings].start_ns;
  numTimings++;

  if (numTimings == MAX_TIMINGS) {
    timings_flush();
  }
}

// Return the size of the ARTT chunk
static jint getChunkSize() {
  jint size = 0;
  size += sizeof(jint); // version
  size += sizeof(jint); // num timing entries.

  size += numTimings *  (sizeof(jint) * 3 + sizeof(jlong) * 2); // entries
  return size;
}

void timings_flush() {
   // Don't even waste a packet if we know it will contain no payload.
   if (numTimings == 0) {
    return;
   }

   PacketOutputStream packet;

   outStream_initCommand(&packet, uniqueID(), 0, JDWP_COMMAND_SET(DDM),  JDWP_COMMAND(DDM, Chunk));

   outStream_writeInt(&packet, 'A' << 24 | 'R' << 16 | 'T' << 8 | 'T');// DDM chunk type
   outStream_writeInt(&packet,  getChunkSize()); // DDM chunk length

   outStream_writeInt(&packet, 1); //version
   outStream_writeInt(&packet, numTimings); // num timing entries

   for(int i=0 ; i < numTimings ; i++) {
     outStream_writeInt(&packet, timings[i].id);
     outStream_writeInt(&packet, timings[i].cmd_set);
     outStream_writeInt(&packet, timings[i].cmd);
     outStream_writeLong(&packet, timings[i].start_ns);
     outStream_writeLong(&packet, timings[i].duration_ns);
   }
   outStream_sendCommand(&packet);
   outStream_destroy(&packet);

   numTimings = 0;
}

