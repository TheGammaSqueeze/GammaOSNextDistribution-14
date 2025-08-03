#include "pthread_impl.h"

void __wait(volatile int *addr, volatile int *waiters, int val, int priv)
{
	/* TRUSTY - no threads yet. */
	a_crash();
}
