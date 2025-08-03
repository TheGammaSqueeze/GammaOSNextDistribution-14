The "service" implementation here does not actually expose a system
service, but is intended as an internal API for a non-mainline
system service to interact with.

This code is needed only to allow calls to the rkpd mainline application,
which lives outside of system server. The rkpd code needs to talk over the
network, and thus it cannot live as part of system server.

The application is also not able to host a system service on its own, so it
requires system server to host a visible service. All service calls to the
system service are proxied via this "service" library to the rkpd application.
