/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * Main driver of the dexdump utility.
 *
 * This is a re-implementation of the original dexdump utility that was
 * based on Dalvik functions in libdex into a new dexdump that is now
 * based on Art functions in libart instead. The output is very similar to
 * to the original for correct DEX files. Error messages may differ, however.
 * Also, ODEX files are no longer supported.
 */

#include <android-base/logging.h>
#include <base/mem_map.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "dexdump.h"

namespace art {

static const char* gProgName = "dexdump";

/*
 * Shows usage.
 */
static void usage() {
  LOG(ERROR) << "Copyright (C) 2007 The Android Open Source Project\n";
  LOG(ERROR) << gProgName
             << ": [-a] [-c] [-d] [-e] [-f] [-h] [-i] [-j] [-l layout] [-n]"
                "  [-s] [-o outfile] dexfile...\n";
  LOG(ERROR) << " -a : display annotations";
  LOG(ERROR) << " -c : verify checksum and exit";
  LOG(ERROR) << " -d : disassemble code sections";
  LOG(ERROR) << " -e : display exported items only";
  LOG(ERROR) << " -f : display dex file header";
  LOG(ERROR) << " -g : display CFG for dex";
  LOG(ERROR) << " -h : display all sections header";
  LOG(ERROR) << " -i : ignore checksum failures";
  LOG(ERROR) << " -j : disable dex file verification";
  LOG(ERROR) << " -l : output layout, either 'plain' or 'xml'";
  LOG(ERROR) << " -n : don't display debug information";
  LOG(ERROR) << " -o : output file name (defaults to stdout)";
  LOG(ERROR) << " -s : display all strings from string_ids header section";
}

/*
 * Main driver of the dexdump utility.
 */
int dexdumpDriver(int argc, char** argv) {
  // Reset options.
  bool wantUsage = false;
  memset(&gOptions, 0, sizeof(gOptions));
  gOptions.verbose = true;
  gOptions.showDebugInfo = true;

  // Parse all arguments.
  while (true) {
    const int ic = getopt(argc, argv, "acdefghijl:no:s");
    if (ic < 0) {
      break;  // done
    }
    switch (ic) {
      case 'a':  // display annotations
        gOptions.showAnnotations = true;
        break;
      case 'c':  // verify the checksum then exit
        gOptions.checksumOnly = true;
        break;
      case 'd':  // disassemble Dalvik instructions
        gOptions.disassemble = true;
        break;
      case 'e':  // exported items only
        gOptions.exportsOnly = true;
        break;
      case 'f':  // display dex file header
        gOptions.showFileHeaders = true;
        break;
      case 'g':  // display cfg
        gOptions.showCfg = true;
        break;
      case 'h':  // display section headers, i.e. all meta-data
        gOptions.showSectionHeaders = true;
        break;
      case 'i':  // continue even if checksum is bad
        gOptions.ignoreBadChecksum = true;
        break;
      case 'j':  // disable dex file verification
        gOptions.disableVerifier = true;
        break;
      case 'l':  // layout
        if (strcmp(optarg, "plain") == 0) {
          gOptions.outputFormat = OUTPUT_PLAIN;
        } else if (strcmp(optarg, "xml") == 0) {
          gOptions.outputFormat = OUTPUT_XML;
          gOptions.verbose = false;
        } else {
          wantUsage = true;
        }
        break;
      case 'n':  // don't display debug information
        gOptions.showDebugInfo = false;
        break;
      case 'o':  // output file
        gOptions.outputFileName = optarg;
        break;
      case 's':  // display all strings
        gOptions.showAllStrings = true;
        break;
      default:
        wantUsage = true;
        break;
    }  // switch
  }  // while

  // Detect early problems.
  if (optind == argc) {
    LOG(ERROR) << "No file specified";
    wantUsage = true;
  }
  if (gOptions.checksumOnly && gOptions.ignoreBadChecksum) {
    LOG(ERROR) << "Can't specify both -c and -i";
    wantUsage = true;
  }
  if (wantUsage) {
    usage();
    return 2;
  }

  // Open alternative output file.
  if (gOptions.outputFileName) {
    gOutFile = fopen(gOptions.outputFileName, "we");
    if (!gOutFile) {
      PLOG(ERROR) << "Can't open " << gOptions.outputFileName;
      return 1;
    }
  }

  // Process all files supplied on command line.
  int result = 0;
  while (optind < argc) {
    result |= processFile(argv[optind++]);
  }  // while
  return result != 0 ? 1 : 0;
}

}  // namespace art

int main(int argc, char** argv) {
  // Output all logging to stderr.
  android::base::SetLogger(android::base::StderrLogger);
  art::MemMap::Init();

  return art::dexdumpDriver(argc, argv);
}
