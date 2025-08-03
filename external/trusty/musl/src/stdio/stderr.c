#include "stdio_impl.h"

#undef stderr

/* TRUSTY - buffer stderr to prevent log fragmentation. */
static unsigned char buf[BUFSIZ+UNGET];
hidden FILE __stderr_FILE = {
	.buf = buf+UNGET,
	.buf_size = sizeof buf-UNGET,
	.fd = 2,
	.flags = F_PERM | F_NORD,
	.lbf = '\n',
	.write = __stdio_write,
	.seek = __stdio_seek,
	.close = __stdio_close,
	.lock = -1,
};
FILE *const stderr = &__stderr_FILE;
FILE *volatile __stderr_used = &__stderr_FILE;
