#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <math.h>
#include <cutils/log.h>

#include "crash_analyzer_defs.h"
#include "crash_analyzer.h"

#define MAX_PATTERN_SIZE 1024
#define MAX_CSTRING_SIZE 8000
#define SEPARATOR " | "

int find_rightmost_setbit_position(uint32_t number)
{
    if (number)
        return log2(number ^ (number & (number - 1)));

    return -1;
}

char* find_exception_in_hmd_dmx(unsigned char *buf, int buf_len,
                                const char *core, char *outbuf,
                                int outbuf_max_size)
{
    char exccause[64] = {0};
    char epc1[32] = {0};
    uint32_t *wordBuf = (uint32_t*)buf;

    if (buf == NULL || buf_len < HMD_DMX_INDEX_MAX * BYTES_PER_WORD ||
        core == NULL || outbuf == NULL || outbuf_max_size <= 0 ||
        wordBuf[START_HMD_DMX_INDEX] == 0 ||
        strlen(outbuf) >= outbuf_max_size)
        return outbuf;

    int excepIndex = wordBuf[EXCEPTION_INDEX];

    if (excepIndex >= HMD_DMXEXCEPTIONCAUSELIST_SIZE) {
        strncpy(exccause, "Reserved", sizeof(exccause));
    }
    else {
        strncpy(exccause, Hmd_DmxExceptionCauseList[excepIndex],
                sizeof(exccause));
    }

    snprintf(epc1, sizeof(epc1), "0x%08x", wordBuf[EPC1_INDEX]);

    if (strlen(outbuf) > 0) {
        strncat(outbuf, SEPARATOR,
                outbuf_max_size - strlen(outbuf) - 1);
    }

    strncat(outbuf, core,
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, " crash ",
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, exccause,
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, "/",
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, epc1,
            outbuf_max_size - strlen(outbuf) - 1);

    return outbuf;
}

char* find_exceptionin_cm4(unsigned char *buf, int buf_len, const char *core,
                           char *outbuf, int outbuf_max_size)
{

    char cause[64] = {0};
    uint32_t *wordBuf = (uint32_t*)buf;

    if (buf == NULL || buf_len < CM4_INDEX_MAX * BYTES_PER_WORD ||
        core == NULL || outbuf == NULL || outbuf_max_size <= 0 ||
        wordBuf[START_CM4_INDEX] == 0 ||
        strlen(outbuf) >= outbuf_max_size)
        return outbuf;

    int faultStatusRegister = wordBuf[FAULT_STATUS_INDEX];

    int exceptionBitSet = find_rightmost_setbit_position(faultStatusRegister);

    if (exceptionBitSet == -1) {
        return outbuf;
    }

    if (exceptionBitSet >= CM4EXCEPTIONCAUSELIST_SIZE) {
        strncpy(cause, "Reserved", sizeof(cause));
    }
    else {
        strncpy(cause, Cm4ExceptionCauseList[exceptionBitSet],
                sizeof(cause));
    }

    if(strlen(outbuf) > 0){
        strncat(outbuf, SEPARATOR,
                outbuf_max_size - strlen(outbuf) - 1);
    }

    strncat(outbuf, core,
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, " crash ",
            outbuf_max_size - strlen(outbuf) - 1);
    strncat(outbuf, cause,
            outbuf_max_size - strlen(outbuf) - 1);

    return outbuf;
}


char* find_fatal_assert(unsigned char *buf, int buf_len, int size,
                        const char *core, char *outbuf,
                        int outbuf_max_size)
{

    char cause[64] = {0};
    char lineNumStr[10] = {0};
    uint32_t *wordBuf = (uint32_t*)buf;
    uint32_t wordSize = size / 4;
    /*
     *|Time Stamp|Event Label,Debug Level,Line No|Opaque Data 1|Opaque Data 2|Opaque Data 3|Opaque Data 4|
     *|(64 bits) | (12 bits), (4 bits), (16 bits)|  (32 bits)  |  (32 bits)  |  (32 bits)  |  (32 bits)  |
     */
    int info_size = 7;
    /* Index of event label word in 64 bit timestamp */
    int curEvtIndex = 2;

    if (buf == NULL || size <= 0 || buf_len < size || core == NULL ||
        outbuf == NULL || outbuf_max_size <= 0 ||
        strlen(outbuf) >= outbuf_max_size)
        return outbuf;

    /* This 32-bit word containing this info
     * is in Little-Endian format to read
     * the value of this fields.
     *
     *  |0...11|  -> Module Id
     *  |12...15| -> Log level
     *  |16...31| -> Line no
     */
    while (curEvtIndex < wordSize) {
        int evt = wordBuf[curEvtIndex];
        int logLevel = (evt >> 12) & 0xF;

        if (logLevel != DBG_LOG_LVL_FATAL) {
            curEvtIndex += info_size;
            continue;
        }

        int module_id = evt & 0xFFF;
        int lineNo = (evt >> 16) & 0xFFFF;

        if (module_id >= ASSERTDEBUGMODLIST_SIZE) {
            /* Invalid module id */
            curEvtIndex += info_size;
            continue;
        }
        else {
            snprintf(cause, sizeof(cause), "%s",
                     AssertDebugModList[module_id]);
        }

        strncat(cause , "+",
                sizeof(cause) - strlen(cause) - 1);

        snprintf(lineNumStr, sizeof(lineNumStr), "%d", lineNo);

        strncat(cause, lineNumStr,
                sizeof(cause) - strlen(cause) - 1);

        if(strlen(outbuf) > 0) {
            strncat(outbuf, SEPARATOR,
                    outbuf_max_size - strlen(outbuf) - 1);
        }

        strncat(outbuf, "ASSERT LOG in ",
                outbuf_max_size - strlen(outbuf) - 1);
        strncat(outbuf, core,
                outbuf_max_size - strlen(outbuf) - 1);
        strncat(outbuf, " ",
                outbuf_max_size - strlen(outbuf) - 1);
        strncat(outbuf, cause,
                outbuf_max_size - strlen(outbuf) - 1);

        return outbuf;
    }

    return outbuf;
}

int analyse_crash_info(const unsigned char *buf, const int buf_len,
                       char *out_crash_analyzer_str,
                       int max_out_crash_analyzer_str_size)
{
    unsigned int file_index = 0, size = 0, tot_len = 0;
    int fcount = 0;
    unsigned char *ptr = NULL;

    if (buf == NULL || out_crash_analyzer_str == NULL ||
        buf_len <= 0 || max_out_crash_analyzer_str_size <= 0) {
        ALOGE("%s: Bad parameters", __func__);
        return -1;
    }

    out_crash_analyzer_str[0] = '\0';

    while ((tot_len + STEP_LENGTH - 1 < buf_len) && (fcount++ < FILE_MAX)) {
        file_index = buf[tot_len];

        size = buf[tot_len + 8] |
            buf[tot_len + 9] << 8 |
            buf[tot_len + 10] << 16 |
            buf[tot_len + 11] << 24;

        tot_len += STEP_LENGTH;
        ptr = (unsigned char*)buf + tot_len;

        if (file_index == CM4_DUMP_DEBUG) {
            find_fatal_assert(ptr, buf_len - tot_len, size, "CM4",
                              out_crash_analyzer_str,
                              max_out_crash_analyzer_str_size);
        } else if(file_index == HMD_DUMP_DEBUG) {
            find_fatal_assert(ptr, buf_len - tot_len, size, "HMD",
                              out_crash_analyzer_str,
                              max_out_crash_analyzer_str_size);
        } else if(file_index == DMX_DUMP_DEBUG) {
            find_fatal_assert(ptr, buf_len - tot_len, size, "DMX",
                              out_crash_analyzer_str,
                              max_out_crash_analyzer_str_size);
        } else if(file_index == CM4_DUMP_CRASH) {
            find_exceptionin_cm4(ptr, buf_len - tot_len, "CM4",
                                 out_crash_analyzer_str,
                                 max_out_crash_analyzer_str_size);
        } else if(file_index == HMD_DUMP_CRASH) {
            find_exception_in_hmd_dmx(ptr, buf_len - tot_len, "HMD",
                                      out_crash_analyzer_str,
                                      max_out_crash_analyzer_str_size);
        } else if(file_index == DMX_DUMP_CRASH) {
            find_exception_in_hmd_dmx(ptr, buf_len - tot_len, "DMX",
                                      out_crash_analyzer_str,
                                      max_out_crash_analyzer_str_size);
        } else {
            ALOGE("%s: unknown index number: %u", __func__ , file_index);
        }

        tot_len += size;
    }

    return strlen(out_crash_analyzer_str);
}
