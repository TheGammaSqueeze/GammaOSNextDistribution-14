#include <stdlib.h>
#include "atomic.h"

#include <trusty_syscalls.h>

_Noreturn void abort(void)
{
	_trusty_exit_etc(127, 0);

	/* Beyond this point should be unreachable. */
	a_crash();
	_Exit(127);
}
