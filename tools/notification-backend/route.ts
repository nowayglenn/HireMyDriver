import { getFirebaseAdminAuth } from "@/lib/firebase/admin";
import { driverDatabase } from "@/lib/admin/drivers";
import { bookingEvents, confirmedBookingPhase, messageEvent, reviewEvent, type NotificationEvent } from "@/lib/notifications/events";
import { deliverEvent } from "@/lib/notifications/delivery";
export const runtime = "nodejs";
const reply = (body: object, status = 200) => Response.json(body, { status, headers: { "Cache-Control": "private, no-store" } });
const validId = (v: unknown): v is string => typeof v === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(v);

// Authenticated relay for already committed events. No client-supplied recipient or notification text.
export async function POST(request: Request) {
  const bearer = request.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!bearer) return reply({ error: "Sign in required" }, 401);
  let uid: string;
  try { const user = await getFirebaseAdminAuth().verifyIdToken(bearer, true); if (user.firebase?.sign_in_provider === "anonymous") throw Error(); uid = user.uid; }
  catch { return reply({ error: "Sign in required" }, 401); }
  let input;
  try { const raw = await request.text(); if (raw.length > 2048) throw Error(); input = JSON.parse(raw); }
  catch { return reply({ error: "Invalid request" }, 400); }
  if (!validId(input?.bookingId) || !["booking", "message", "review"].includes(input?.kind) || (input.kind === "message" && !validId(input.messageId))) return reply({ error: "Invalid event" }, 400);
  const db = driverDatabase();
  try {
    let events: NotificationEvent[];
    if (input.kind === "message") {
      const thread = db.collection("conversations").doc(input.bookingId);
      const [t, m] = await Promise.all([thread.get(), thread.collection("messages").doc(input.messageId).get()]);
      if (!t.exists || !m.exists) return reply({ error: "Event not found" }, 404);
      events = [messageEvent(input.bookingId, input.messageId, t.data()!, m.data()!, uid)];
    } else {
      const booking = await db.collection("bookings").doc(input.bookingId).get();
      if (!booking.exists) return reply({ error: "Booking not found" }, 404);
      if (input.kind === "review") {
        const review = await db.collection("reviews").doc(input.bookingId).get();
        if (!review.exists) return reply({ error: "Review not found" }, 404);
        events = [reviewEvent(input.bookingId, booking.data()!, review.data()!, uid)];
      } else {
        // Delayed delivery may replay only a transition confirmed in the booking itself.
        const data = booking.data()!;
        if (uid !== data.customerUid && uid !== data.driverUid) throw Error("ACCESS");
        const phase = confirmedBookingPhase(data, input.phase);
        if (!phase) return reply({ skipped: true });
        events = bookingEvents(input.bookingId, { ...data, phase }, uid);
      }
    }
    for (const event of events) await deliverEvent(event);
    return reply({ ok: true });
  } catch (error) {
    const reason = error instanceof Error ? error.message : "";
    if (reason === "ACCESS") return reply({ error: "Forbidden" }, 403);
    if (reason === "EVENT") return reply({ error: "Invalid event" }, 400);
    console.error("Notification relay failed", error instanceof Error ? error.name : "Error");
    return reply({ error: "Delivery unavailable; retry later" }, 503);
  }
}
