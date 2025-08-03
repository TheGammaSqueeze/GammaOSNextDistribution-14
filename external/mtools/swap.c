/*  Copyright 2021 Alain Knaff.
 *  This file is part of mtools.
 *
 *  Mtools is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Mtools is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Mtools.  If not, see <http://www.gnu.org/licenses/>.
 *
 * filter to support byte-swapped filesystems
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "swap.h"

typedef struct Swap_t {
	struct Stream_t head;
} Swap_t;

static void swap_buffer(char *buf, size_t len)
{
	unsigned int i;
	for (i=0; i<len; i+=2) {
		char temp = buf[i];
		buf[i] = buf[i+1];
		buf[i+1] = temp;
	}
}


static ssize_t swap_pread(Stream_t *Stream, char *buf,
			  mt_off_t where, size_t len)
{
	DeclareThis(Swap_t);

	ssize_t result = PREADS(This->head.Next, buf, where, len);
	if(result < 0)
		return result;
	swap_buffer( buf, (size_t) result);
	return result;
}

static ssize_t swap_pwrite(Stream_t *Stream, char *buf,
			  mt_off_t where, size_t len)
{
	DeclareThis(Swap_t);

	ssize_t result;
	char *swapping = malloc( len );
	memcpy( swapping, buf, len );
	swap_buffer( swapping, len );

	result = PWRITES(This->head.Next, swapping, where, len);

	free(swapping);
	return result;
}


static Class_t SwapClass = {
	0,
	0,
	swap_pread,
	swap_pwrite,
	0, /* flush */
	0, /* free */
	set_geom_pass_through, /* set_geom */
	0, /* get_data */
	0, /* pre-allocate */
	get_dosConvert_pass_through, /* dos convert */
	0, /* discard */
};

Stream_t *OpenSwap(Stream_t *Next) {
	Swap_t *This;

	This = New(Swap_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(Swap_t));
	init_head(&This->head, &SwapClass, Next);

	return &This->head;
}
