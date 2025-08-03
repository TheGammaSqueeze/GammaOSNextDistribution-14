/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.*;
import java.util.*;

// usage: java ZoneCompactor <setup file> <data directory> <output directory> <tzdata version>
//
// Compile a set of tzfile-formatted files into a single file containing an index.
//
// The compilation is controlled by a setup file, which is provided as a
// command-line argument.  The setup file has the form:
//
// Link <toName> <fromName>
// ...
// <zone filename>
// ...
//
// Note that the links must be declared prior to the zone names.
// A zone name is a filename relative to the source directory such as
// 'GMT', 'Africa/Dakar', or 'America/Argentina/Jujuy'.
//
// Use the 'zic' command-line tool to convert from flat files
// (such as 'africa' or 'northamerica') to a directory
// hierarchy suitable for this tool (containing files such as 'data/Africa/Abidjan').
//

public class ZoneCompactor {
  // Maximum number of characters in a zone name, including '\0' terminator.
  private static final int MAXNAME = 40;

  // Zone name synonyms.
  private Map<String,String> links = new HashMap<>();

  // File offsets by zone name.
  private Map<String,Integer> offsets = new HashMap<>();

  // File lengths by zone name.
  private Map<String,Integer> lengths = new HashMap<>();

  // Concatenate the contents of 'inFile' onto 'out'.
  private static void copyFile(File inFile, OutputStream out) throws Exception {
    InputStream in = new FileInputStream(inFile);
    byte[] buf = new byte[8192];
    while (true) {
      int nbytes = in.read(buf);
      if (nbytes == -1) {
        break;
      }
      out.write(buf, 0, nbytes);
    }
    out.flush();
  }

  public ZoneCompactor(String setupFile, String dataDirectory, String outputDirectory,
          String version) throws Exception {
    // Read the setup file and concatenate all the data.
    Set<String> zoneIds = new LinkedHashSet<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(setupFile))) {
      String s;
      while ((s = reader.readLine()) != null) {
        s = s.trim();
        StringTokenizer st = new StringTokenizer(s);
        String lineType = st.nextToken();
        if (lineType.startsWith("Link")) {
          String to = st.nextToken();
          String from = st.nextToken();
          links.put(from, to);
        } else if (lineType.startsWith("Zone")) {
          String zoneId = st.nextToken();
          if (!zoneIds.add(zoneId)) {
            throw new IllegalStateException(String.format("There are at least two Zone entries "
                    + "for %s in the setup file", zoneId));
          }
        }
      }
    }

    ByteArrayOutputStream allData = new ByteArrayOutputStream();

    int offset = 0;
    for (String zoneId : zoneIds) {
      if (!links.containsKey(zoneId)) {
        File sourceFile = new File(dataDirectory, zoneId);
        long length = sourceFile.length();
        offsets.put(zoneId, offset);
        lengths.put(zoneId, (int) length);

        offset += length;
        copyFile(sourceFile, allData);
      }
    }

    // Fill in fields for links.
    for (String from : links.keySet()) {
      String to = links.get(from);

      offsets.put(from, offsets.get(to));
      lengths.put(from, lengths.get(to));
    }

    // Create/truncate the destination file.
    RandomAccessFile f = new RandomAccessFile(new File(outputDirectory, "tzdata"), "rw");
    f.setLength(0);

    // Write the header.

    // byte[12] tzdata_version -- 'tzdata2012f\0'
    // int index_offset -- so we can slip in extra header fields in a backwards-compatible way
    // int data_offset
    // int final_offset

    // tzdata_version
    f.write(toAscii(new byte[12], version));

    // Write placeholder values for the offsets, and remember where we need to seek back to later
    // when we have the real values.
    int index_offset_offset = (int) f.getFilePointer();
    f.writeInt(0);
    int data_offset_offset = (int) f.getFilePointer();
    f.writeInt(0);
    // The final offset serves as a placeholder for sections that might be added in future and
    // ensures we know the size of the final "real" section. Relying on the last section ending at
    // EOF would make it harder to append sections to the end of the file in a backward compatible
    // way.
    int final_offset_offset = (int) f.getFilePointer();
    f.writeInt(0);

    int index_offset = (int) f.getFilePointer();

    // Write the index.
    ArrayList<String> sortedOlsonIds = new ArrayList<String>();
    sortedOlsonIds.addAll(offsets.keySet());
    Collections.sort(sortedOlsonIds);
    for (String zoneName : sortedOlsonIds) {
      if (zoneName.length() >= MAXNAME) {
        throw new RuntimeException("zone filename too long: " + zoneName.length());
      }

      // Follow the chain of links to work out where the real data for this zone lives.
      String actualZoneName = zoneName;
      while (links.get(actualZoneName) != null) {
        actualZoneName = links.get(actualZoneName);
      }

      f.write(toAscii(new byte[MAXNAME], zoneName));
      f.writeInt(offsets.get(actualZoneName));
      f.writeInt(lengths.get(actualZoneName));
      f.writeInt(0); // Used to be raw GMT offset. No longer used.
    }

    int data_offset = (int) f.getFilePointer();

    // Write the data.
    f.write(allData.toByteArray());

    int final_offset = (int) f.getFilePointer();

    // Go back and fix up the offsets in the header.
    f.seek(index_offset_offset);
    f.writeInt(index_offset);
    f.seek(data_offset_offset);
    f.writeInt(data_offset);
    f.seek(final_offset_offset);
    f.writeInt(final_offset);

    f.close();
  }

  private static byte[] toAscii(byte[] dst, String src) {
    for (int i = 0; i < src.length(); ++i) {
      if (src.charAt(i) > '~') {
        throw new RuntimeException("non-ASCII string: " + src);
      }
      dst[i] = (byte) src.charAt(i);
    }
    return dst;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      System.err.println("usage: java ZoneCompactor <setup file> <data directory>"
              + " <output directory> <tzdata version>");
      System.exit(1);
    }
    new ZoneCompactor(args[0], args[1], args[2], args[3]);
  }
}
