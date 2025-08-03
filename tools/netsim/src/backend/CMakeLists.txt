cmake_policy(SET CMP0079 NEW)
android_add_library(
  TARGET packet-streamer-client-lib
  LICENSE Apache-2.0
  SRC packet_streamer_client.cc packet_streamer_client.h
  DEPS android-emu-base
       android-emu-base-headers
       android-emu-base-process
       android-emu-metrics
       android-grpc-interceptors
       grpc++
       packet-streamer-proto-lib
       protobuf::libprotobuf
       util-lib)
target_include_directories(packet-streamer-client-lib PUBLIC ..)
