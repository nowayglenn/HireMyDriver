
import { NextResponse } from "next/server";
import { getAdminSession } from "@/lib/auth/session";
import { trustedOrigin } from "@/lib/auth/policy";
import { driverDatabase, FieldValue } from "@/lib/admin/drivers";
import { applicationEntry, decision } from "@/lib/admin/driver-contract";
import { approvalEvent } from "@/lib/notifications/events";
import { deliverEvent } from "@/lib/notifications/delivery";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const headers = { "Cache-Control": "private, no-store" };
export async function GET() {
  if (!await getAdminSession()) return NextResponse.json({ error: "Please sign in again." }, { status: 401, headers });
  try {
    const snapshot = await driverDatabase().collection("driverApplications").get();
    const applications = snapshot.docs.map(doc => applicationEntry(doc.id, doc.data()));
    applications.sort((a, b) => b.submitted.localeCompare(a.submitted));
    return NextResponse.json({ applications }, { headers });
  } catch { return NextResponse.json({ error: "Unable to load driver applications. Please try again." }, { status: 503, headers }); }
}
export async function POST(request: Request) {
  if (!trustedOrigin(request.headers.get("origin"), process.env.APP_ORIGIN)) return NextResponse.json({ error: "Request not allowed." }, { status: 403, headers });
  const actor = await getAdminSession();
  if (!actor) return NextResponse.json({ error: "Please sign in again." }, { status: 401, headers });
  let review;
  try {
    if (!request.headers.get("content-type")?.startsWith("application/json")) throw new Error();
    const text = await request.text(); if (text.length > 4096) throw new Error();
    const body = JSON.parse(text); if (!body || Array.isArray(body)) throw new Error();
    review = decision(body);
  } catch { return NextResponse.json({ error: "Invalid review. Rejection requires a reason." }, { status: 400, headers }); }
  try {
    const db = driverDatabase();
    const auditRef = db.collection("driverReviewAudit").doc();
    const approved = review.status === "approved" && review.expectedStatus !== "approved"
      ? approvalEvent(review.id, auditRef.id) : null;
    await db.runTransaction(async transaction => {
      const ref = db.collection("driverApplications").doc(review.id);
      const snapshot = await transaction.get(ref);
      const source = snapshot.data();
      if (!source || source.submissionId !== review.submissionId || source.status !== review.expectedStatus) throw new Error("STALE_REVIEW");
      transaction.update(ref, { status: review.status, reviewReason: review.reason,
        reviewedBy: actor.uid, reviewedAt: FieldValue.serverTimestamp() });
      // Update the customer directory atomically, without exposing application documents.
      transaction.set(db.collection("driverProfiles").doc(review.id), {
        fullName: source.fullName, experience: source.experience, bio: source.bio, status: review.status,
      }, { merge: true });
      transaction.set(auditRef, { driverId: review.id,
        submissionId: review.submissionId, from: source.status, to: review.status,
        reason: review.reason, actorUid: actor.uid, createdAt: FieldValue.serverTimestamp() });
      // The alert and verified status commit together; failed/conflicting decisions create neither.
      if (approved) transaction.create(db.collection("users").doc(review.id).collection("notifications").doc(approved.eventId),
        { ...approved, read: false, createdAt: FieldValue.serverTimestamp() });
    });
    if (approved) {
      // A push outage must not undo an approval. The durable in-app notification is already saved.
      try { await deliverEvent(approved); }
      catch { console.error("Approval push delivery unavailable; notification saved."); }
    }
    return NextResponse.json({ ok: true }, { headers });
  } catch (error) {
    const stale = error instanceof Error && error.message === "STALE_REVIEW";
    return NextResponse.json({ error: stale ? "This application changed. Refresh before reviewing again." : "Unable to save the decision. Please retry." }, { status: stale ? 409 : 503, headers });
  }
}
