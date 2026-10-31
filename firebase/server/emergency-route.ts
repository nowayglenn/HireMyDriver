import { getFirebaseAdminAuth } from "@/lib/firebase/admin";
import { driverDatabase } from "@/lib/admin/drivers";
import { FieldValue } from "firebase-admin/firestore";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const headers = { "Cache-Control": "private, no-store" };
const reply = (body: object, status = 200) => Response.json(body, { status, headers });
const idPattern = /^[A-Za-z0-9_-]{1,128}$/;
const clean = (value: unknown, maximum = 500) => typeof value === "string" ? value.replace(/[\r\n]+/g, " ").slice(0, maximum) : "Not provided";
const providerStatus = (value: unknown) => {
  const status = String(value).toLowerCase();
  return ["queued", "pending", "sent", "failed", "refunded"].includes(status) ? status : "unknown";
};

export async function POST(request: Request) {
  const token = request.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!token) return reply({ error: "Please sign in again." }, 401);
  let uid: string;
  try {
    const user = await getFirebaseAdminAuth().verifyIdToken(token, true);
    if (user.firebase?.sign_in_provider === "anonymous") return reply({ error: "Please sign in." }, 401);
    uid = user.uid;
  } catch { return reply({ error: "Please sign in again." }, 401); }
  let input;
  try {
    const raw = await request.text();
    if (raw.length > 4096) return reply({ error: "Invalid alert request." }, 400);
    input = JSON.parse(raw);
  } catch { return reply({ error: "Invalid alert request." }, 400); }
  if (!input || typeof input.bookingId !== "string" || !idPattern.test(input.bookingId) || input.consent !== true)
    return reply({ error: "Confirm the emergency alert first." }, 400);
  const key = process.env.SEMAPHORE_API_KEY;
  const sender = process.env.SEMAPHORE_SENDERNAME;
  if (process.env.EMERGENCY_SMS_ENABLED !== "true" || !key || !sender)
    return reply({ error: "Emergency SMS is not configured yet. No SMS was sent." }, 503);
  const db = driverDatabase();
  const ref = db.collection("emergencyAlerts").doc(input.bookingId);
  try {
    const claimed = await db.runTransaction(async tx => {
      const [bookingDoc, contactDoc, previous] = await Promise.all([
        tx.get(db.collection("bookings").doc(input.bookingId)),
        tx.get(db.collection("users").doc(uid).collection("settings").doc("emergencyContact")), tx.get(ref)
      ]);
      const booking = bookingDoc.data();
      if (!booking || booking.customerUid !== uid) throw Error("ACCESS");
      if (previous.exists) return { existing: previous.data()!, phone: "", message: "" };
      if (!["ACCEPTED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS"].includes(booking.phase)) throw Error("INACTIVE");
      const contact = contactDoc.data();
      if (!contact || !/^\+639\d{9}$/.test(contact.phone)) throw Error("CONTACT");
      const now = Date.now();
      const loc = input.location;
      const validLocation = loc && Number.isFinite(loc.lat) && Math.abs(loc.lat) <= 90
        && Number.isFinite(loc.lon) && Math.abs(loc.lon) <= 180 && Number.isFinite(loc.time)
        && now - loc.time >= -10000 && now - loc.time <= 120000;
      const location = validLocation ? `${loc.lat.toFixed(6)}, ${loc.lon.toFixed(6)}` : "Current location could not be determined";
      const when = new Date(now);
      const date = new Intl.DateTimeFormat("en-PH", { timeZone: "Asia/Manila", dateStyle: "long" }).format(when);
      const time = new Intl.DateTimeFormat("en-PH", { timeZone: "Asia/Manila", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: true }).format(when);
      const destinations = Array.isArray(booking.destinations) && booking.destinations.length
        ? booking.destinations.map((place: { label?: string }) => clean(place.label, 150)).join(" -> ") : clean(booking.destinationLabel);
      const message = ["EMERGENCY ALERT - Hire My Driver", "I need immediate assistance during my trip.",
        `Customer: ${clean(booking.customer, 200)}`, `Current Location: ${location}`,
        validLocation ? `View My Location: https://maps.google.com/?q=${loc.lat},${loc.lon}` : "View My Location: unavailable",
        ...(contact.tripDetails ? [`Pickup Location: ${clean(booking.pickupLabel)}`, `Destination: ${destinations}`, `Driver: ${clean(booking.driver, 200)}`] : []),
        `Date: ${date}`, `Time: ${time} (Philippine Standard Time)`,
        "Please contact me or seek assistance immediately.", "Generated through the Hire My Driver application."].join("\n\n");
      tx.create(ref, { customerUid: uid, bookingId: input.bookingId, status: "sending", createdAt: FieldValue.serverTimestamp() });
      return { existing: null, phone: contact.phone as string, message };
    });
    if (claimed.existing) {
      const previous = claimed.existing;
      if (previous.providerId && ["queued", "pending"].includes(previous.status)) {
        const url = `https://api.semaphore.co/api/v4/messages/${encodeURIComponent(previous.providerId)}?apikey=${encodeURIComponent(key)}`;
        const response = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(15000), redirect: "error" });
        if (response.ok) {
          const data = await response.json();
          const status = providerStatus((Array.isArray(data) ? data[0] : data)?.status);
          if (status !== "unknown") { await ref.update({ status }); return reply({ status, duplicate: true }); }
        }
      }
      return reply({ status: previous.status === "sending" ? "unknown" : previous.status, duplicate: true });
    }
    // The durable claim prevents double sends, including after a timeout or process restart.
    // Unknown outcomes must be reconciled in the provider dashboard; never blindly retry a POST.
    try {
      const response = await fetch("https://api.semaphore.co/api/v4/priority", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ apikey: key, sendername: sender, number: claimed.phone.replace(/^\+/, ""), message: claimed.message }),
        signal: AbortSignal.timeout(20000), redirect: "error", cache: "no-store"
      });
      const data = await response.json();
      const item = Array.isArray(data) ? data[0] : null;
      if (!response.ok || !item?.message_id) {
        await ref.update({ status: "unknown" });
        return reply({ status: "unknown" });
      }
      const status = providerStatus(item.status);
      await ref.update({ status, providerId: String(item.message_id) });
      return reply({ status });
    } catch {
      await ref.update({ status: "unknown" });
      return reply({ status: "unknown" });
    }
  } catch (error) {
    const reason = error instanceof Error ? error.message : "";
    return reply({ error: reason === "CONTACT" ? "Save an emergency contact in Settings first."
      : reason === "INACTIVE" ? "Emergency SMS requires an active booking."
      : reason === "ACCESS" ? "This booking is not available to your account."
      : "Could not check the alert. Please retry to check its status; do not assume it was sent." }, reason === "ACCESS" ? 403 : 503);
  }
}
