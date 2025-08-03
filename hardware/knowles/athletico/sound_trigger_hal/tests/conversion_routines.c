#include <stdint.h>

static void kst_split_aft(
    uint32_t* pAfloat,
    int32_t*  exp,
    int64_t*  mant,
    int32_t*  sign)
{
    uint32_t uAft = *pAfloat;

    *exp  = (uAft >> 25) & 0x3F;
    *mant =  uAft & 0x1FFFFFF;
    *sign =  uAft >> 31;
    if (*exp || *mant)
    {
        *mant |= 1 << 25;
    }
}

static void kst_aft_to_dbl(
    void* pDouble,
    void* pAfloat)
{
    uint64_t uDbl;
    int32_t  exp;
    int32_t  sign;
    int64_t  mant;

    kst_split_aft((uint32_t*)pAfloat, &exp, &mant, &sign);
    if (exp || mant)
    {
        uDbl = ((uint64_t)sign << 63) |
               ((uint64_t)(exp + (1023 - (1 << 5))) << 52) |
               ((uint64_t)(mant & ((1 << 25) - 1)) << (52 - 25));
    }
    else
    {
        uDbl = (uint64_t)sign << 63;
    }
    *((uint64_t*)pDouble) = uDbl;
}

void kst_float_to_q15_vector(
    void*    pDst,
    void*    pSrc,
    uint32_t elCnt)
{
    uint32_t* pSrcT;
    int16_t*  pDstT;
    uint32_t  idx;
    double    smp;

    pSrcT = (uint32_t*)pSrc;
    pDstT = (int16_t*)pDst;
    for (idx = 0; idx<elCnt; idx++)
    {
        kst_aft_to_dbl(&smp, &(pSrcT[idx]));
        smp = smp * 32768.0;
        pDstT[idx] = ((smp < 32767.0) ? ((smp > -32768.0) ? ((int16_t)smp) : -32768) : 32767);
    }
}

void kst_float_to_IEEE_float(
    void*    pDst,
    void*    pSrc)
{
    double dst;

    kst_aft_to_dbl(&dst, pSrc);
    *((float*)pDst) = (float)dst;
}