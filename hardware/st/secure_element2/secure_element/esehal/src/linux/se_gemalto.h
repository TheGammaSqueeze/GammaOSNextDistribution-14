/*
 * se_gemalto.h - Access Gemalto Secure Element over SPI.
 *
 * 2019 Gemalto – a Thales Company
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

#ifndef SE_GEMALTO_H
#define SE_GEMALTO_H

#include <linux/types.h>

#define GTO_IOC_MAGIC           'g'

/* Read / Write of power configuration (GTO_POWER_ON, GTO_POWER_OFF) */
#define GTO_IOC_RD_POWER        _IOR(GTO_IOC_MAGIC, 1, __s32)
#define GTO_IOC_WR_POWER        _IOW(GTO_IOC_MAGIC, 1, __s32)
#define GTO_IOC_WR_RESET        _IOW(GTO_IOC_MAGIC, 2, __s32)

/* Read / Write of clock speed configuration */
#define GTO_IOC_RD_CLK_SPEED    _IOR(GTO_IOC_MAGIC, 2, __s32)
#define GTO_IOC_WR_CLK_SPEED    _IOW(GTO_IOC_MAGIC, 2, __s32)

#endif /* ifndef SE_GEMALTO_H */
