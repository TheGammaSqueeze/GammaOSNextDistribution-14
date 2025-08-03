#include <trusty/string.h>

#include <stdio.h>
#include <stdlib.h>

int scnprintf(char* buf, size_t size, const char* fmt, ...) {
    va_list args;

    va_start(args, fmt);
    int vscn_ret = vscnprintf(buf, size, fmt, args);
    va_end(args);

    return vscn_ret;
}

int vscnprintf(char* buf, size_t size, const char* fmt, va_list args) {
    if (size == 0) {
        return 0;
    }
    int would_write = vsnprintf(buf, size, fmt, args);
    if (would_write < 0) {
        return would_write;
    }
    size_t max_write = size - 1;
    if ((size_t)would_write > max_write) {
        return (int)max_write;
    }
    return would_write;
}
