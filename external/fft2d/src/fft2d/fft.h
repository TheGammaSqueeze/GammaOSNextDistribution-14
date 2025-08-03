// Copyright 2006 Google Inc. All Rights Reserved.
// Author: popat@google.com (Ashok C. Popat)
//
// Declarations for third-party 1D FFT routines in third_party/fft2d/fft2d.

#ifndef THIRD_PARTY_FFT2D_FFT_H__
#define THIRD_PARTY_FFT2D_FFT_H__

#ifdef __cplusplus
extern "C" {
#endif

extern void cdft(int, int, double *, int *, double *);
extern void rdft(int, int, double *, int *, double *);
extern void ddct(int, int, double *, int *, double *);
extern void ddst(int, int, double *, int *, double *);
extern void dfct(int, double *, double *, int *, double *);
extern void dfst(int, double *, double *, int *, double *);

#ifdef __cplusplus
}
#endif

#endif  // THIRD_PARTY_FFT2D_FFT_H__
