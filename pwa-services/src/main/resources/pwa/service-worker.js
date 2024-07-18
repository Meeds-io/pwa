self.addEventListener('install', event => self.skipWaiting());
self.addEventListener('push', async (event) => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const action = data.split(':')[1]
    if (action === 'closeAll') {
      const notifications = await self.registration.getNotifications();
      if (notifications?.length) {
        notifications.forEach(notification => notification.close());
      }
    } else if (action === 'close') {
      const notificationId = data.split(':')[0]
      const notifications = await self.registration.getNotifications();
      if (notifications?.length) {
        const notification = notifications.find(notification => notification.tag === notificationId);
        notification?.close?.();
      }
    } else if (action === 'open') {
      const notificationId = data.split(':')[0]
      const webNotification = await fetch(`/pwa/rest/notifications/${notificationId}`, {
        method: 'GET',
        credentials: 'include',
      }).then(resp => resp.ok && resp.json());
      if (webNotification) {
        const title = webNotification.title || '';
        delete webNotification.title;
        webNotification.icon = webNotification.icon || self.location.origin + '/pwa/rest/manifest/smallIcon?sizes=72x72';
        webNotification.data = {
          notificationId,
          path: webNotification.url || '/',
        };
        delete webNotification.url;
        if (!webNotification.tag) {
          delete webNotification.tag;
        }
        if (!webNotification.image) {
          delete webNotification.image;
        }
        if (!webNotification.lang) {
          delete webNotification.lang;
        }
        if (!webNotification.dir) {
          delete webNotification.dir;
        }
        if (!webNotification.body) {
          delete webNotification.body;
        }
        if (!webNotification.badge) {
          delete webNotification.badge;
        }
        if (!webNotification.vibrate) {
          delete webNotification.vibrate;
        }
        if (!webNotification.renotify) {
          delete webNotification.renotify;
        }
        if (!webNotification.requireInteraction) {
          delete webNotification.requireInteraction;
        }
        if (!webNotification.silent) {
          delete webNotification.silent;
        }
        await self.registration.showNotification(title, webNotification);
        const notifications = await self.registration.getNotifications();
        navigator.setAppBadge(notifications?.length);
      }
    }
  }
});
self.addEventListener('notificationclick', (event) => {
  const notificationId = event.notification.data.notificationId;
  const path = event.notification.data.path;
  event.waitUntil(new Promise(async (resolve, reject) => {
    await fetch(`/pwa/rest/notifications/${notificationId}`, {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: 'action=markRead'
    });
    event.notification.close();
    const notifications = await self.registration.getNotifications();
    navigator.setAppBadge(notifications?.length);
    try {
      clients.openWindow(self.location.origin + event.notification.data.path);
    } catch(e) {
      console.error(e);
      reject(e);
    }
    resolve();
  }));
});
