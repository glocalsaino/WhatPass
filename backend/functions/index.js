const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

// Shared secret PassSource includes in the Authorization header. Stored in
// Secret Manager (set with `firebase functions:secrets:set PASSKIT_PUSH_RELAY_SECRET`),
// never committed to source control.
const RELAY_SECRET = defineSecret("PASSKIT_PUSH_RELAY_SECRET");

/**
 * POST /pushPassUpdate
 * Header: Authorization: Bearer <shared secret>
 * Body: { "pushToken": "...", "passTypeIdentifier": "...", "serialNumber": "..." }
 *
 * Relays a "this pass changed" ping to the WhatPass Android app via FCM, so
 * PassSource never has to hold or rotate a Firebase credential directly -
 * only this one shared secret, which we control and can rotate ourselves.
 */
exports.pushPassUpdate = onRequest(
  { secrets: [RELAY_SECRET], cors: false, region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method Not Allowed");
      return;
    }

    const authHeader = req.get("Authorization") || "";
    const expected = `Bearer ${RELAY_SECRET.value()}`;
    if (authHeader !== expected) {
      logger.warn("Rejected request with invalid Authorization header");
      res.status(401).send("Unauthorized");
      return;
    }

    const { pushToken, passTypeIdentifier, serialNumber } = req.body || {};

    // TEMPORARY (PassSource integration debugging): log every request PassSource's
    // relay call actually reaches us with, so we have our own record to compare
    // against their timestamps - independent of whether the request is well-formed.
    // Token is redacted to a prefix/suffix, matching how PassSource said they log it.
    const redactedToken = pushToken
      ? `${pushToken.slice(0, 10)}...${pushToken.slice(-6)} (len ${pushToken.length})`
      : "(missing)";
    logger.info("pushPassUpdate request received", {
      pushToken: redactedToken,
      passTypeIdentifier: passTypeIdentifier || "(missing)",
      serialNumber: serialNumber || "(missing)",
    });

    if (!pushToken || !passTypeIdentifier || !serialNumber) {
      res.status(400).send("Missing pushToken, passTypeIdentifier or serialNumber");
      return;
    }

    try {
      // Data-only message: MiWalletFirebaseService.onMessageReceived() reads
      // message.data directly and fetches the update itself - no "notification"
      // payload here, or it would show a generic OS notification instead.
      // android.priority "high" matters specifically because this is a data-only
      // message: FCM defaults those to normal priority, which Doze can defer until
      // the device's next maintenance window while it's asleep - high priority is
      // delivered immediately regardless.
      const messageId = await admin.messaging().send({
        token: pushToken,
        data: { passTypeIdentifier, serialNumber },
        android: { priority: "high" },
      });
      logger.info(`Sent FCM message ${messageId} for ${passTypeIdentifier}/${serialNumber}`);
      res.status(200).json({ success: true, messageId });
    } catch (error) {
      logger.error("FCM send failed", error);
      res.status(502).json({ success: false, error: String(error) });
    }
  }
);
