# Driver help and support

- Driver Home no longer has a menu icon. The greeting retains its previous position, and the notification button remains available.
- Profile → Help Center opens a dedicated screen with searchable, expandable answers and a Contact Support button.
- Profile → Contact Support opens a dedicated form with a topic picker, optional booking reference, and required message. Both screens have a back button and use the existing navy/orange style without bottom navigation.
- Share message opens the Android share chooser. The driver chooses the destination app and recipient and sends from there. No support email, phone number, ticket service, or automatic submission is configured. Opening the chooser is not reported as successful delivery, and the message remains in the form.
- Form text and the selected topic survive activity recreation using view state and saved instance state. Nothing is added to persistent support storage.

## Device checks when testing resumes

1. Open Driver Home and confirm the menu icon is absent and the notification bell and greeting are unchanged.
2. Open Help Center from Profile. Search for documents, expand an answer, clear the search, and check an unmatched query.
3. Open Contact Support from both Profile and Help Center. Select a topic and try sharing with an empty message; an inline error should appear.
4. Enter a booking reference and message, rotate the device, and confirm the form remains intact.
5. Tap Share message and inspect the composed text in a chosen app without sending. Cancel and confirm the form remains available. Check that Back returns to the previous screen.
6. Check smaller screens, larger text sizes, and the keyboard: the full form and share action should remain reachable by scrolling.

Phone testing remains deferred at the user's request.
