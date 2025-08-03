ExtServices module - Notification
=============================

### NotificationAssistantService
A service that helps the user to manage notifications. Only one notification assistant can be
active at a time. Unlike notification listener services, assistant services can additionally
modify certain aspects about notifications Adjustment before they are posted.

The system tells ExtServices about every new notification, ExtServices sends a message back about
the smart replies/actions and decides whether the notification needs be demoted (silence or show
lower in the shade), If ExtServices fails, framework just shows all notifications.

### Test
- MTS