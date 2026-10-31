# Driver refresh

Swipe-down refresh is available at the top of Driver Home and both Home View all destinations: Upcoming Bookings and Booking Requests. It uses the existing AndroidX refresh component with an orange circular indicator. Headers, cards, navigation, and booking button behavior are retained. There is an accessibility refresh action as well.

These three screens currently use demo data. Home reruns its existing sample data loader and rereads saved availability without changing it. The two View all screens reload their original XML sample cards and reconnect their existing buttons. Booking Requests preserves each card's current visibility so requests already accepted or declined in that screen do not reappear during refresh. No server requests, booking mutations, or synthetic delays are added. The separate Driver Booking tab and its local trip store are unchanged.

The indicator ends after the local reload is rendered, including failures, and pending UI work is cancelled when leaving the screen. Since the data is local, a reload can finish quickly.

Phone verification is deferred: pull at the top of all three screens; scroll normally when not at the top; verify availability stays unchanged; accept/decline a sample request then refresh and confirm it remains hidden; verify remaining card/buttons still navigate correctly; leave during refresh and return without a stuck indicator.
