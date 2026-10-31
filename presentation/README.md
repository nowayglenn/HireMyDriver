# HireMyDriver presentation

Open **HireMyDriver-Presentation.html** in Chrome or Edge. The single file includes the app's original logo, onboarding artwork, images, styles, and interactive demonstration. It works offline and can be copied to another computer without an assets folder.

## Presenting

- Start with onboarding, then choose Customer or Driver.
- Demo login details are prefilled. Do not enter real credentials.
- The left panel lists all presentation screens. Search for a feature, click a screen, or use the arrows below the phone to move through the presentation.
- **Focus mode** gives the phone more space. Use the browser's full-screen shortcut for a presentation.
- Switch roles using the Customer / Driver controls. Sample bookings, messages, extensions, and notifications are shared within the open demo.
- **Restart demo** returns to onboarding and resets sample bookings. Reloading the file also resets the session.

## Suggested end-to-end walkthrough

1. Customer → Home → driver profile → Book Now. Choose a weekday between 6 AM and 6 PM; choose pickup first, then one or more destinations. Review and send.
2. Switch to Driver → New Booking Requests → Accept. Open the booking map, head to pickup, confirm arrival, then start service.
3. Switch to Customer → Bookings → Accepted → Track Trip. Request extra time or another destination.
4. Switch to Driver → select the same trip → map. Accept the extension/destination request. Confirm each stop; the final stop completes the booking.
5. Switch to Customer → Completed → Rate Driver → submit a review. View the completed route history or rebook the driver.
6. Explore messages, notifications, driver requirements, availability, service areas, account settings, and support.

## Coverage and limits

The demo recreates the current Android layouts and interactions in HTML. It reuses original artwork and navy/orange colors; browser fonts and controls may differ slightly from Android. It includes both roles, four onboarding pages, registration, recovery, discovery, filtering, profiles, booking, trip stages, extensions, additional stops, cancellation, completed history, reviews, messages, notifications, documents, schedules, emergency contacts, settings, and support.

Maps are offline illustrations of Bohol, not live Google Maps. Panning and pin retention are interactive. The satellite option changes the illustrative map style; it is not satellite imagery. Authentication, document uploads, GPS, payment, emergency actions, and delivery of notifications/messages are simulations. Legal pages use the policy text bundled in the Android app. Sample data does not represent actual driver availability, ratings, pricing, or customer information. Nothing connects to Firebase or sends external requests. Demo state remains in memory and resets when reloaded.

## Source

`index.html`, `presentation.css`, and `app.js` are editable. The Android XML resources are exported by `export-android.ps1` into `android-resources.js`. `android-renderer.js` handles their sizing and positioning; `android-screens.js`, `android-flow.js`, `android-polish.js`, and `android-followup.js` bind the demo content and actions. Run `node presentation/build.mjs` from the Android project folder to rebuild the standalone HTML. For local preview, run `node presentation/serve.mjs` and open http://127.0.0.1:8765.

The location prompt appears on the first Home visit for each demo role, after login or a presentation role switch. Onboarding and login do not display it. Returning to Home does not repeat the prompt; Restart demo resets it.

Driver Bookings has Requests, Upcoming, and Completed tabs. An active trip appears separately above those tabs. Customer Bookings has All, Pending, Accepted, Completed, and Cancelled. Home, Bookings, Messages, and Profile share the same bottom navigation geometry and selected-tab appearance.

The Android source and app behavior are unchanged by this presentation.

The selected navigation tab uses orange outline icons and labels; inactive tabs use gray outlines. All four main screens share the same bar height and spacing in both roles. Layout fixes also cover centered registration labels, login dividers, multiline fields, and route connectors.

Customer search supports names and areas, a clear (X) button, and recent searches with individual removal and Clear all. Search history lasts for the open demo session. Map sheets can be dragged; the map controls follow their position. License scanning is a camera-free demonstration.

The role-selection background fills the entire phone display. Driver requests, upcoming bookings, and upcoming schedules use separate Android list layouts. Both Help screens include the original FAQ text, searchable expandable answers, and Contact Support. My Reviews lists only the demo customer’s submitted reviews across drivers, with edit options and two sample past reviews. Language checkboxes retain their selection during the demo session.
