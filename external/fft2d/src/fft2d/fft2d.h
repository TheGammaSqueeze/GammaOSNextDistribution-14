// Copyright 2006 Google Inc. All Rights Reserved.
// Author: popat@google.com (Ashok C. Popat)
//
// Declarations for third-party 2D FFT routines in third_party/fft2d/fft2d.

#ifndef THIRD_PARTY_FFT2D_FFT2D_H__
#define THIRD_PARTY_FFT2D_FFT2D_H__

#ifdef __cplusplus
extern "C" {
#endif

extern void cdft2d(int, int, int, double **, double *, int *, double *);
extern void rdft2d(int, int, int, double **, double *, int *, double *);
extern void ddct2d(int, int, int, double **, double *, int *, double *);
extern void ddst2d(int, int, int, double **, double *, int *, double *);
extern void ddct8x8s(int isgn, double **a);
extern void ddct16x16s(int isgn, double **a);

#ifdef __cplusplus
}
#endif

#endif  // THIRD_PARTY_FFT2D_FFT2D_H__
