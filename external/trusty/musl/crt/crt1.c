#include <features.h>
#include "libc.h"

int main();
weak void _init();
weak void _fini();
int __libc_start_main(int (*)(), int, char **,
	void (*)(), void(*)(), void(*)());

/*
 * TRUSTY - renamed from _start_c because it can be invoked directly without an
 * ASM stub. The stack will be aligned according to the ABI, and the argument
 * will be in the expected register.
 */
void _start(long *p)
{
	int argc = p[0];
	char **argv = (void *)(p+1);
	__libc_start_main(main, argc, argv, _init, _fini, 0);
}
