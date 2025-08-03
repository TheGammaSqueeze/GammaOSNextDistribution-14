$ANDROID_BUILD_TOP/external/perfetto/tools/record_android_trace \
  -c $ANDROID_BUILD_TOP/packages/apps/DocumentsUI/perfetto_config.pbtx \
  -o /tmp/perfetto-traces/docsui-$(date +"%d-%m-%Y_%H-%M-%S").perfetto-trace
