#ifndef _CONVERSION_ROUTINES_H_
#define _CONVERSION_ROUTINES_H_

void kst_float_to_q15_vector(
    void*    pDst,
    void*    pSrc,
    uint32_t elCnt);

void kst_float_to_IEEE_float(
    void*    pDst,
    void*    pSrc);

#endif // ifndef _CONVERSION_ROUTINES_H_
