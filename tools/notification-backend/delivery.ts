import { getFirebaseAdminAuth } from "@/lib/firebase/admin";
import { driverDatabase } from "@/lib/admin/drivers";
import { getMessaging } from "firebase-admin/messaging";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import type { NotificationEvent } from "@/lib/notifications/events";

/** Persist first, deduplicate retries, then send to the recipient's own registered devices. */
export async function deliverEvent(event: NotificationEvent) {
  const db = driverDatabase();
  const ref = db.collection("users").doc(event.uid).collection("notifications").doc(event.eventId);
  const lease = await db.runTransaction(async tx => {
    const old = await tx.get(ref);
    if (old.get("delivered") === true) return false;
    if (old.exists && (old.get("leaseUntil") as Timestamp | undefined)?.toMillis()! > Date.now()) throw Error("BUSY");
    tx.set(ref, { ...event, createdAt: old.get("createdAt") ?? FieldValue.serverTimestamp(),
      read: old.get("read") === true, leaseUntil: Timestamp.fromMillis(Date.now() + 60000) }, { merge: true });
    return true;
  });
  if (!lease) return;
  try {
    const devices = await db.collection("users").doc(event.uid).collection("devices").get();
    const eligible = devices.docs.filter(d => typeof d.get("token") === "string" && d.get("role") === event.role);
    let retry = false;
    for (let i = 0; i < eligible.length; i += 500) {
      const group = eligible.slice(i, i + 500);
      const result = await getMessaging(getFirebaseAdminAuth().app).sendEachForMulticast({
        tokens: group.map(d => d.get("token")), data: event,
        android: { priority: "high", ttl: 86400000 },
      });
      await Promise.all(result.responses.map(async (r, n) => {
        if (r.success) return;
        if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(r.error?.code ?? "")) await group[n].ref.delete();
        else retry = true;
      }));
    }
    await ref.update({ delivered: !retry, leaseUntil: Timestamp.fromMillis(0) });
    if (retry) throw Error("RETRY");
  } catch (error) { await ref.update({ leaseUntil: Timestamp.fromMillis(0) }); throw error; }
}
