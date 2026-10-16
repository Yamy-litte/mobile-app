const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Every in-app notification also becomes a device push notification.
 * Tokens are stored on customers/{uid} by the authenticated Android client.
 */
exports.pushReaderNotification = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const notification = event.data && event.data.data();
    if (!notification || !notification.customerUid) {
      console.warn("Notification is missing customerUid", event.params.notificationId);
      return;
    }

    const customerRef = getFirestore().collection("customers").doc(notification.customerUid);
    const customer = await customerRef.get();
    if (!customer.exists) {
      console.warn("Reader profile not found", notification.customerUid);
      return;
    }

    const token = customer.get("fcmToken");
    if (typeof token !== "string" || !token.trim()) {
      console.warn("Reader has not registered an FCM token", notification.customerUid);
      return;
    }

    try {
      await getMessaging().send({
        token,
        notification: {
          title: notification.title || "MyLibrary",
          body: notification.content || "Bạn có thông báo mới.",
        },
        data: {
          title: notification.title || "MyLibrary",
          body: notification.content || "Bạn có thông báo mới.",
          notificationId: event.params.notificationId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "library_updates",
            sound: "default",
          },
        },
      });
      console.log("Push notification sent", event.params.notificationId);
    } catch (error) {
      console.error("Unable to send push notification", error);
      if (error.code === "messaging/registration-token-not-registered"
          || error.code === "messaging/invalid-registration-token") {
        await customerRef.update({ fcmToken: null, fcmTokenUpdatedAt: Date.now() });
      }
    }
  }
);
