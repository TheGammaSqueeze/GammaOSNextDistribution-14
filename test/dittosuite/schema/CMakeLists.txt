find_package(Protobuf REQUIRED)

set(Protobuf_USE_STATIC_LIBS ON)

include_directories(${Protobuf_INCLUDE_DIRS})
protobuf_generate_cpp(PROTO_SRC PROTO_HDR benchmark.proto result.proto)
add_library(benchmark_proto ${PROTO_HDR} ${PROTO_SRC})
target_link_libraries(benchmark_proto INTERFACE ${Protobuf_LIBRARIES})
