VERS_1.0 {
  # Export JNI symbols.
  global:
    JNI_OnLoad;

  # Hide everything else
  local:
    *;
};
