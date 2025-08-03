#include <stdio.h>
#include <sys/types.h>
#include <errno.h>
#include <time.h>

long long diff_time(struct timespec *start, struct timespec *end)
{
	long long diff;
	diff = (end->tv_sec - start->tv_sec) * 1000 * 1000;
	diff += (end->tv_nsec - start->tv_nsec) / 1000;
	return diff;
}

int Sleep(int ms)
{
	struct timespec ts;
	struct timespec rem;

	ts.tv_sec = ms / 1000;
	ts.tv_nsec = (ms % 1000) * 1000 * 1000;
	for (;;) {
		if (nanosleep(&ts, &rem) == 0) {
			break;
		} else {
			if (errno == EINTR) {
				ts = rem;
				continue;
			}
			return -1;
		}
	}
	return 0;
}

void print_buffer(const unsigned char *buf, unsigned int len)
{
	for (unsigned int i = 0; i < len; ++i) {
		fprintf(stdout, "0x%02X ", buf[i]);
		if (i % 8 == 7)
			fprintf(stdout, "\n");
	}
	fprintf(stdout, "\n");
}


const char * StripPath(const char * path, ssize_t size)
{
	int i;
	const char * str;

	for (i = size - 1, str = &path[size - 1]; i > 0; --i, --str)
		if (path[i - 1] == '/')
			break;

	return str;
}

unsigned long extract_long(const unsigned char *data)
{
	return (unsigned long)data [0]
		+ (unsigned long)data [1] * 0x100
		+ (unsigned long)data [2] * 0x10000
		+ (unsigned long)data [3] * 0x1000000;
}

unsigned short extract_short(const unsigned char *data)
{
	return (unsigned long)data [0]
		+ (unsigned long)data [1] * 0x100;
}