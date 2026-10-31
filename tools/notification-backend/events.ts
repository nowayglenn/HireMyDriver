export type NotificationEvent = { uid: string; role: "customer" | "driver"; kind: "booking" | "message" | "review" | "approval"; bookingId: string; eventId: string; title: string; body: string; phase?: string };
const titles: Record<string, string> = { REQUESTED: "Booking submitted", ACCEPTED: "Booking accepted", ON_THE_WAY: "Driver on the way", ARRIVED: "Driver has arrived", IN_PROGRESS: "Trip started", COMPLETED: "Trip completed", CANCELLED: "Booking canceled", DECLINED: "Booking declined" };
const bodies: Record<string, string> = {
  REQUESTED: "Your booking request has been submitted.", ACCEPTED: "Your driver accepted your booking.",
  ON_THE_WAY: "Your driver is on the way to your pickup location.", ARRIVED: "Your driver has arrived at your pickup location.",
  IN_PROGRESS: "Your booked service has started. Your trip timer is now running.", COMPLETED: "Your trip is complete. Thank you for riding.",
  CANCELLED: "Your booking has been canceled.", DECLINED: "Your booking request was declined."
};
// Historical events are accepted only when the booking contains the server-confirmed transition receipt.
export function confirmedBookingPhase(booking: Record<string, unknown>, requested: unknown): string | null {
  if (typeof requested !== "string" || !titles[requested]) return null;
  if (booking.phase === requested) return requested;
  const receipt = (booking.statusEvents as Record<string, unknown> | undefined)?.[requested];
  return receipt && typeof (receipt as {toMillis?: unknown}).toMillis === "function" ? requested : null;
}
export function bookingEvents(id: string, booking: Record<string, unknown>, caller: string): NotificationEvent[] {
  if (caller !== booking.customerUid && caller !== booking.driverUid) throw Error("ACCESS");
  const phase = String(booking.phase);
  if (!titles[phase]) throw Error("EVENT");
  return ["customer", "driver"].map(role => ({ uid: String(booking[role + "Uid"]), role: role as "customer" | "driver",
    kind: "booking" as const, phase, bookingId: id, eventId: `booking_${id}_${phase}`,
    title: role === "driver" && phase === "REQUESTED" ? "New booking request" : titles[phase],
    body: role === "customer" ? bodies[phase] : "Open the booking to view the latest details." }));
}
export function messageEvent(id: string, messageId: string, thread: Record<string, unknown>, message: Record<string, unknown>, caller: string): NotificationEvent {
  if (message.senderId !== caller || (caller !== thread.customerId && caller !== thread.driverId)) throw Error("ACCESS");
  const driver = caller === thread.customerId;
  return { uid: String(driver ? thread.driverId : thread.customerId), role: driver ? "driver" : "customer", kind: "message",
    bookingId: id, eventId: `message_${id}_${messageId}`, title: String(driver ? thread.customerName : thread.driverName).slice(0, 100) || "New message",
    body: String(message.text ?? "New message").replace(/[\r\n\t]/g, " ").slice(0, 140) };
}

// A review alert is derived from a committed review and its completed booking.
export function reviewEvent(id: string, booking: Record<string, unknown>, review: Record<string, unknown>, caller: string): NotificationEvent {
  if (caller !== booking.customerUid || review.customerUid !== caller || review.driverUid !== booking.driverUid) throw Error("ACCESS");
  if (booking.phase !== "COMPLETED" || !Number.isInteger(review.rating) || Number(review.rating) < 1 || Number(review.rating) > 5) throw Error("EVENT");
  return { uid: String(booking.driverUid), role: "driver", kind: "review", bookingId: id,
    eventId: `review_${id}`, title: "New customer review",
    body: `A customer rated your completed service ${review.rating}/5. Tap to read the review.` };
}

// Called only by the authenticated admin decision route, never from the app relay.
export function approvalEvent(driverId: string, decisionId: string): NotificationEvent {
  return { uid: driverId, role: "driver", kind: "approval", bookingId: "",
    eventId: `approval_${decisionId}`, title: "Driver account verified",
    body: "Your driver registration has been approved by the admin. Open your profile to view your status." };
}
