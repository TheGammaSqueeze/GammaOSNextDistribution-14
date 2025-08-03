#ifndef _CRASH_PROCESS_H_
#define _CRASH_PROCESS_H_

#define STEP_LENGTH 12
#define BYTES_PER_WORD 4

int analyse_crash_info(const unsigned char *buf, const int buf_len,
                       char *out_crash_analyzer_str,
                       int max_out_crash_analyzer_str_size);
#endif
