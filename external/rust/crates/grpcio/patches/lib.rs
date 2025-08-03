diff --git a/src/lib.rs b/src/lib.rs
index c8cebca..ba47f95 100644
--- a/src/lib.rs
+++ b/src/lib.rs
@@ -29,6 +29,12 @@ use grpcio_sys as grpc_sys;
 #[macro_use]
 extern crate log;

+// grpcio with feature protobufv3-codec expects a "protobufv3" dependency,
+// but Android.bp is not able to rename dependencies yet. See b/308790322.
+// It is enabled unconditionally since protobufv3-codec also is.
+#[cfg(soong)]
+extern crate protobuf as protobufv3;
+
 mod buf;
 mod call;
 mod channel;
