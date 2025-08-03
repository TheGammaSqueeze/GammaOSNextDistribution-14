# Minijailed Media Codec (MMC)

MMC is a service running in minijail that isolates codec implementations from
the Bluetooth process and system resources. It is an independent daemon that
spawns codec servers on demand to communicate with their corresponding codec
clients living in Floss.

## Steps to Apply MMC to a New Codec

### 1. Implement codec server

  * Wraps third party library codes.
  * Codec server should inherit [MMC Interface](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/system/stack/mmc/mmc_interface/mmc_interface.h).
    * public methods: `init`, `cleanup`, `transcode`.
    * `init`: set up transcoder and return frame size accepted by the transcoder.
    * `cleanup`: clear the transcoder context.
    * `transcode`: transcode input data, store result in the given output buffer,
                   and return the transcoded data length.

### 2. Add codec proto message in [mmc_config.proto](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/system/stack/mmc/proto/mmc_config.proto)

  * Define a proto message for the new codec, generally, it may include:
    * Init configuration.
    * Transcode arguments or params.
    * Library-specific enum mappings.
  * Append an entry to [`ConfigParam`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/system/stack/mmc/proto/mmc_config.proto;drc=1e6b2d44402d18cce637f4b02d4da25133924662;l=99).

### 3. Add codec support in MMC daemon

  * Match the new `ConfigParam` to create its corresponding server in [`CodecInit`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/system/stack/mmc/daemon/service.cc;drc=e9fcc3a7897c6af3df4163534688290778e2333b;l=186).

### 4. Access the interface via [`CodecClient`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/system/stack/mmc/codec_client/codec_client.h) from within the BT process

  * BT process accesses codec implementations via `CodecClient`.
    * `init`: set up `ConfigParam` and pass it to `CodecClient`.
    * `transcode`: pass input and output buffer, and specify the input data size
                   and the output buffer capacity. `transcode` returns transcoded
                   data length on success, and negative error number otherwise.
    * `cleanup`: when a session ends, `cleanup` should be called.

## Related links

* Design doc: go/floss-mmc
* Slides: go/floss-mmc-presentation
* Performance evaluation: go/floss-mmc-experiment
