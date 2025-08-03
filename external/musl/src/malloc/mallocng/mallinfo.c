#include <limits.h>
#include <malloc.h>
#include <stddef.h>

#include "glue.h"
#include "meta.h"

static void accumulate_meta(struct mallinfo2 *mi, struct meta *g)
{
	int sc = g->sizeclass;
	if (sc >= 48) {
		// Large mmap allocation
		mi->hblks++;
		mi->uordblks += g->maplen*4096;
		mi->hblkhd += g->maplen*4096;
	} else {
		if (g->freeable && !g->maplen) {
			// Small size slots are embedded in a larger slot, avoid
			// double counting by subtracting the size of the larger
			// slot from the total used memory.
			struct meta* outer_g = get_meta((void*)g->mem);
			int outer_sc	= outer_g->sizeclass;
			int outer_sz = size_classes[outer_sc]*UNIT;
			mi->uordblks -= outer_sz;
			mi->arena -= outer_sz;
		}
		int sz = size_classes[sc]*UNIT;
		int mask = g->avail_mask | g->freed_mask;
		int nr_unused = a_popcount_32(mask);

		if (!g->last_idx) {
			// Allocation groups with a single allocation may use a
			// smaller maplen than normally used for the size class.
			if (sz > g->maplen-IB-UNIT) {
				sz = g->maplen-IB-UNIT;
			}
		}
		mi->arena += sz*(g->last_idx+1);
		mi->ordblks += nr_unused;
		mi->uordblks += sz*(g->last_idx+1-nr_unused);
		mi->fordblks += sz*nr_unused;
	}
}

static void accumulate_meta_area(struct mallinfo2 *mi, struct meta_area *ma)
{
	for (int i=0; i<ma->nslots; i++) {
		if (ma->slots[i].mem) {
			accumulate_meta(mi, &ma->slots[i]);
		}
	}
}

struct mallinfo2 mallinfo2(void)
{
	struct mallinfo2 mi = {0};

	rdlock();
	struct meta_area *ma = ctx.meta_area_head;
	while (ma) {
		accumulate_meta_area(&mi, ma);
		ma = ma->next;
	}
	unlock();

	return mi;
}

#define cap(x) ((x > INT_MAX) ? INT_MAX : x)

struct mallinfo mallinfo(void) {
	struct mallinfo mi = {0};
	struct mallinfo2 mi2 = mallinfo2();

	mi.arena = cap(mi2.arena);
	mi.ordblks = cap(mi2.ordblks);
	mi.smblks = cap(mi2.smblks);
	mi.hblks = cap(mi2.hblks);
	mi.hblkhd = cap(mi2.hblkhd);
	mi.usmblks = cap(mi2.usmblks);
	mi.fsmblks = cap(mi2.fsmblks);
	mi.uordblks = cap(mi2.uordblks);
	mi.fordblks = cap(mi2.fordblks);
	mi.keepcost = cap(mi2.keepcost);

	return mi;
}
