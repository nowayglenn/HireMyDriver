# Customer notification design

Customer Notifications keeps its existing navy header, date groups, row layout, colored circular icons, and unread dots. Promo Unlocked, Payment Received, and Security Update were removed from the sample feed.

The replacement examples cover booking accepted/declined, driver on the way/arrived, a customer-facing driver message, an upcoming booking reminder, trip started/completed, a driver-cancelled booking, and destination change accepted/declined.

This is a labeled design preview, not a live feed. Text and timestamps illustrate notification types, and tapping opens a sample detail dialog rather than a real booking. No server subscriptions, push delivery, or reminder scheduling were added. Existing system trip-timer notifications remain unchanged.

Tapping an item marks it read. Mark all read clears every unread dot. Customer preview read state is saved locally, separately from Driver Notifications.

Phone testing remains deferred. On-device checks should cover the notification list, individual read actions, Mark all read, state after reopening, and Back navigation.
