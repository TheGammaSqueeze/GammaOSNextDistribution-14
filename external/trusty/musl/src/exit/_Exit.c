#include <stdlib.h>
#include <trusty_syscalls.h>

_Noreturn void _Exit(int ec)
{
	for (;;) _trusty_exit_etc(ec, 0);
}
