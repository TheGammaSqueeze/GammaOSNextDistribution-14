## Media V2 CTS Tests
Current folder comprises of files necessary for testing media extractor, media muxer, media codec SDK and NDK Api. These tests aims to test all codecs advertised in MediaCodecList, available muxers and extractors.

The aim of these tests is not solely to verify the CDD requirements but also to test components, their plugins and their interactions with media framework.

The test vectors used by the test suite is available at [link](https://dl.google.com/android/xts/cts/tests/media/CtsMediaV2TestCases-4.0.zip) and is downloaded automatically while running tests. Manual installation of these can be done using copy_media.sh script in this directory.

All Big Buck Bunny(bbb) test vectors are of 8-bit format. They are downloaded from [link](https://peach.blender.org/download/) and resampled according to the test requirements.
All Cosmos Laundromat(cosmat) test vectors are of 10-bit format. They are downloaded from [link](https://media.xiph.org/) and resampled according to the test requirements.
VideoEncoderParamTest uses stefan_sif_yuv420p_30fps.yuv. This is downloaded from [link](https://media.xiph.org/).

The test suite looks to cover sdk/ndk api in normal and error scenarios. Error scenarios are separated from regular usage and are placed under class *UnitTest (MuxerUnitTest, ExtractorUnitTest, ...).

### Commands
```sh
$ atest android.mediav2.cts
$ atest android.mediav2.cts.CodecEncoderTest android.mediav2.cts.CodecDecoderTest
$ atest android.mediav2.cts.MuxerTest android.mediav2.cts.MuxerUnitTest
$ atest android.mediav2.cts.ExtractorTest android.mediav2.cts.ExtractorUnitTest
```

### Features
All tests accepts attributes that offer selective run of tests.

#### Select codecs by name
To select codecs by name, *codec-prefix* can be passed to media codec tests to select one or more codecs that start with a given prefix.

Example: To limit the tests to run for codecs whose names start with c2.android.

```sh
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2.android.
```

Example: To limit the tests to run for c2.android.hevc.decoder

```sh
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2.android.hevc.decoder
```

#### Select codecs using regular expressions
To select codecs by applying regular expressions, *codec-filter* can be passed to media codec tests to select one or more codecs that match with a specified regular expression pattern.

Example: To limit the tests to run for c2.android.avc.encoder and c2.exynos.hevc.encoder

```sh
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:codec-filter:="c2\.android\.avc\.encoder\|c2\.exynos\.hevc\.encoder"
```

#### Select codecs by type
To select codecs by type, *media-type-sel* can be passed to media codec tests to select one or more codecs.

Example: To limit media codec decoder tests to mp3 and vorbis decoder

```sh
atest android.mediav2.cts.CodecDecoderTest -- --module-arg  CtsMediaV2TestCases:instrumentation-arg:media-type-sel:=mp3,vorbis
```

#### Select extractors by type
To select extractors by type, *ext-sel* can be passed to extractor tests to select one or more extractors.

Example: To limit extractor tests to mp4 and webm types
```sh
atest android.mediav2.cts.ExtractorTest -- --module-arg  CtsMediaV2TestCases:instrumentation-arg:ext-sel:=mp4,webm
```

#### Select muxers by type
To select muxers by type, *mux-sel* can be passed to muxer tests to select one or more muxers.

Example: To limit muxer tests to mp4 and webm types
```sh
atest android.mediav2.cts.MuxerTest -- --module-arg  CtsMediaV2TestCases:instrumentation-arg:mux-sel:=mp4,webm
```

### Appendix
| Identifier for codec-sel | Mime |
| ------ | ------ |
|default|all|
|vp8|mimetype_video_vp8|
|vp9|mimetype_video_vp9|
|av1|mimetype_video_av1|
|avc|mimetype_video_avc|
|hevc|mimetype_video_hevc|
|mpeg4|mimetype_video_mpeg4|
|h263|mimetype_video_h263|
|mpeg2|mimetype_video_mpeg2|
|vraw|mimetype_video_raw|
|amrnb|mimetype_audio_amr_nb|
|amrwb|mimetype_audio_amr_wb|
|mp3|mimetype_audio_mpeg|
|aac|mimetype_audio_aac|
|vorbis|mimetype_audio_vorbis|
|opus|mimetype_audio_opus|
|g711alaw|mimetype_audio_g711_alaw|
|g711mlaw|mimetype_audio_g711_mlaw|
|araw|mimetype_audio_raw|
|flac|mimetype_audio_flac|
|gsm|mimetype_audio_msgsm|


| Identifier for ext-sel | Extractor format |
| ------ | ------ |
|mp4|Mpeg4|
|webm|Matroska|
|3gp|Mpeg4|
|mkv|Matroska|
|ogg|Ogg|


| Identifier for mux-sel | Muxer Format |
| ------ | ------ |
|mp4|muxer_output_mpeg4|
|webm|muxer_output_webm|
|3gp|muxer_output_3gpp|
|ogg|muxer_output_ogg|
