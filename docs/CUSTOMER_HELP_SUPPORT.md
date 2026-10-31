# Customer help and header consistency

Change Password now uses the shared screen-header minimum height, title size, margins, and header tag. BaseActivity can therefore paint the status bar navy with light icons, using the same safe insets as other screens. The title can wrap for larger text sizes. Password validation and submission behavior were not changed by this visual update.

Customer Settings → Help & Support now opens CustomerHelpCenterActivity. It matches the driver help design, with a navy header, rounded cards, searchable expandable questions, an empty-search message, and Contact Support. Answers cover customer bookings, pickup selection, returning to an active trip, location permissions, notifications, and preparing a support message.

Contact Support uses the same neutral form layout as the driver screen, with customer-specific topics and a customer support heading in shared message text. The message is required; the booking reference is optional. Topic and form state survive recreation. Sharing opens Android's chooser and retains the form. No official support recipient or delivery backend has been configured, so the screen does not claim a message has been sent.

Driver screens and unrelated customer UI are unchanged. No bottom navigation is added to these help/detail screens.

When phone testing resumes, check status-bar color and header height, larger text, Help & Support from Settings, search and expanded answers, Contact Support validation, Back navigation, rotation, and cancelling the share chooser. Device testing remains deferred.
