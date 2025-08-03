This contains the c2 version of emulator's hardware decoders

decoders/ contains avc(a.k.a. h264) and vpx(vp8 and vp9) decoders
and base. All are based upon c2 sw codecs.

store/ the store that creates decoders
this is also borrowed from c2.

service/ the hidl service that required by platform;
to actually get it work, need to set this in file_contexts
/vendor/bin/hw/android\.hardware\.media\.c2@1\.0-service-goldfish u:object_r:mediacodec_exec:s0
