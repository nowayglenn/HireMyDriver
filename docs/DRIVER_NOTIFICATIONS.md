# Driver notification design

The bell on Driver Home opens DriverNotificationsActivity. The new screen follows the customer Notifications layout: navy header, Back, Mark all read, Today/Yesterday/Earlier groups, colored circular icons, separators, and unread dots. It reuses item_notification.xml without modifying the customer screen or shared item design.

The screen is explicitly labeled as a design preview with sample notifications. Its eight examples cover a booking request, customer message, trip reminder, destination change request, cancellation, customer review, verification update, and license reminder. Their text and relative times are illustrative, not actual account events.

Tapping an item marks it read and opens its sample detail dialog. Mark all read clears every unread dot. Preview read state is saved separately on this phone and survives reopening the screen. Back returns to Driver Home; there is no bottom navigation on this detail screen.

No server notification feed, scheduled reminder, push delivery, verification decision, or license-expiry check is implemented by this design change. Existing trip timer/system notifications remain separate.

Phone testing remains deferred. When resumed, check the Home bell, scrolling through all groups, reading individual items, Mark all read, reopening, and Back navigation.
