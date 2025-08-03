/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <cstdio>
#include <memory>

#include <stdlib.h>

#include <audio_utils/BiquadFilter.h>
#include <audio_utils/channels.h>
#include <audio_utils/primitives.h>

using namespace android::audio_utils;

// Random a coefficients here for the test.
static constexpr std::array<float, kBiquadNumCoefs> biquadCoefs = {
        0.983097f, -1.966194f, 0.983097f, -1.988931f, 0.988992f};

void printUsage() {
    printf("\nUsage:");
    printf("\n     <executable> -ch:<process_channels> -fch:<file_channels> "
        "<input_file> <out_file>");
    printf("\nwhere,\n     <process_channels> (1 through 8) default 2");
    printf("\n     <file_channels> (1 through 8) default 2\n");
    printf("\n     <inputfile>  is the input file name");
    printf("\n                  on which filtering is applied");
    printf("\n     <outputfile> processed output file\n");

}

int main(int argc, const char *argv[]) {

    if (argc < 3) {
        printUsage();
        return EXIT_FAILURE;
    }

    int fileChannelCount = 2;
    int procChannelCount = 2;

    if (argc < 3) {
        printf("Error: missing input/output files\n");
        printUsage();
        return EXIT_FAILURE;
    }

    const char *infile = argv[argc - 2];
    const char *outfile = argv[argc - 1];

    if (infile == nullptr || outfile == nullptr) {
        printf("Error: missing input/output files\n");
        printUsage();
        return EXIT_FAILURE;
    }

    printf("%s ", infile);
    printf("%s ", outfile);

    for (int i = 1; i < argc - 2; i++) {
        printf("%s ", argv[i]);
        if (!strncmp(argv[i], "-ch:", 4)) {
            procChannelCount = atoi(argv[i] + 4);
            if (procChannelCount < 1 || procChannelCount > 8) {
                printf("\nNumber of channels to process exceeds limit\n");
                return EXIT_FAILURE;
            }
        } else if (!strncmp(argv[i], "-fch:", 5)) {
            fileChannelCount = atoi(argv[i] + 5);
            if (fileChannelCount < 1 || fileChannelCount > 8) {
                printf("\nNumber of channels in the file exceeds limit\n");
                return EXIT_FAILURE;
            }
        }
    }

    std::unique_ptr<std::FILE, decltype(&std::fclose)> finp(
            std::fopen(infile, "rb"), &std::fclose);
    if (finp == nullptr) {
        printf("Cannot open input file %s\n", infile);
        return EXIT_FAILURE;
    }

    std::unique_ptr<std::FILE, decltype(&std::fclose)> fout(
            std::fopen(outfile, "wb"), &std::fclose);
    if (fout == nullptr) {
        printf("Cannot open output file %s\n", outfile);
        fclose(finp.get());
        return EXIT_FAILURE;
    }

    const size_t frameLength = 256;
    const int maxChannelCount = std::max(fileChannelCount, procChannelCount);

    std::vector<short> in(frameLength * maxChannelCount);
    std::vector<short> out(frameLength * maxChannelCount);
    std::vector<float> floatIn(frameLength * procChannelCount);
    std::vector<float> floatOut(frameLength * procChannelCount);
    const size_t ioFrameSize = fileChannelCount * sizeof(short);

    BiquadFilter biquadFilter(procChannelCount);
    biquadFilter.clear();
    biquadFilter.setCoefficients(biquadCoefs);

    size_t frameCounter = 0;

    while (fread(in.data(), ioFrameSize, frameLength, finp.get()) ==
           frameLength) {
        if (fileChannelCount != procChannelCount) {
            adjust_channels(in.data(), fileChannelCount, in.data(),
                            procChannelCount, sizeof(short),
                            frameLength * ioFrameSize);
        }
        memcpy_to_float_from_i16(floatIn.data(), in.data(),
                                 frameLength * procChannelCount);

        biquadFilter.process(floatOut.data(), floatIn.data(), frameLength);

        memcpy_to_i16_from_float(out.data(), floatOut.data(),
                                 frameLength * procChannelCount);
        frameCounter += frameLength;
        adjust_channels(out.data(), procChannelCount, out.data(),
                        fileChannelCount, sizeof(short),
                        frameLength * procChannelCount * sizeof(short));
        fwrite(out.data(), ioFrameSize, frameLength, fout.get());
    }
    printf("frameCounter: [%zu]\n", frameCounter);

    return EXIT_SUCCESS;
}
