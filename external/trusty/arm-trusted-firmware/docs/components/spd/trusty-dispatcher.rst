Trusty Dispatcher
=================

Trusty is a a set of software components, supporting a Trusted Execution
Environment (TEE) on mobile devices, published and maintained by Google.

Detailed information and build instructions can be found on the Android
Open Source Project (AOSP) webpage for Trusty hosted at
https://source.android.com/security/trusty

Build flags
-----------

The ``TRUSTY_SPD_WITH_SHARED_MEM`` build flag controls whether Trusty SPD
is built with memory sharing support.

The ``TRUSTY_SPD_WITH_GENERIC_SERVICES`` build flag controls whether
Trusty SPD supports smc calls to return gic base address and print to
the debug console.


C flags
-----------

On platforms which use ATF BL2, Trusty is already mapped in.
If the dynamic mapping function is available, it will fail on a range
overlap, and it may not be enabled by default on all platforms.
As a result, we only enable premapping for the Tegra platform, using
the ``LATE_MAPPED_BL32`` CFLAG

Boot parameters
---------------

Custom boot parameters can be passed to Trusty by providing a platform
specific function:

.. code:: c

    void plat_trusty_set_boot_args(aapcs64_params_t *args)

If this function is provided ``args->arg0`` must be set to the memory
size allocated to trusty. If the platform does not provide this
function, but defines ``TSP_SEC_MEM_SIZE``, a default implementation
will pass the memory size from ``TSP_SEC_MEM_SIZE``. ``args->arg1``
can be set to a platform specific parameter block, and ``args->arg2``
should then be set to the size of that block.

Platform hooks
--------------

Sharing memory using the Trusty SPD requires the ``plat_mem_set_shared()``
function to be implemented. This function is documented in the header file
services/spd/trusty/include/trusty/plat/shared_mem.h


Supported platforms
-------------------

Out of all the platforms supported by Trusted Firmware-A, Trusty is only
verified and supported by NVIDIA's Tegra SoCs.
