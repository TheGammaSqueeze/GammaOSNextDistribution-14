#include <CL/cl.h>
#include <CL/cl_gl.h>
#include <CL/cl_egl.h>
#include <CL/cl_ext.h>
#include <dlfcn.h>


void* libHandle = nullptr;

#define FUNC_TYPES(rettype, fname, fargs, callArgs)     \
typedef rettype (* fname ## _t) fargs;

#define FUNC_SYM(rettype, fname, fargs, callArgs)                                                     \
rettype fname fargs {                                                                                 \
  if (!libHandle)                                                                                     \
    libHandle = dlopen( "libOpenCL.so", RTLD_NOW | RTLD_GLOBAL );                                     \
                                                                                                      \
  static fname ## _t func = nullptr;                                                                  \
  if (!func)                                                                                          \
    func = reinterpret_cast< fname ## _t >(dlsym(libHandle, #fname));                                 \
                                                                                                      \
  return func callArgs;                                                                               \
                                                                                                      \
}

#define CL_MACRO FUNC_TYPES
#include "apis.h"
#undef CL_MACRO

#define CL_MACRO FUNC_SYM
#include "apis.h"
#undef CL_MACRO
