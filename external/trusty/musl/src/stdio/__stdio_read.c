#include "stdio_impl.h"
#include <sys/uio.h>

size_t __stdio_read(FILE *f, unsigned char *buf, size_t len)
{
	/* TRUSTY - no read syscall. */
	f->flags |= F_EOF;
	return 0;
}
