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
          url: self.location.origin + (webNotification.url || '/'),
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
        if (!Notification.maxActions || !webNotification.actions) {
          delete webNotification.actions;
        } else if (webNotification.actions.length > Notification.maxActions) {
          webNotification.actions = webNotification.actions.slice(0, Notification.maxActions);
        }
        await self.registration.showNotification(title, webNotification);
        await refreshBadge();
      }
    }
  }
});

self.addEventListener('notificationclick', (event) => {
  const url = event.notification.data.url;
  event.waitUntil(new Promise(async (resolve) => {
    event.notification.close();
    if (event.action) {
      await updateNotification(event.notification.data.notificationId, event.action);
    } else {
      try {
        const windows = await clients.matchAll({ type: "window" });
        const focused = windows
          .some(w => w.url === url ? (windowClient.focus(), true) : false);
        if (focused) {
          console.debug('window with url ', url, ' focused');
        } else {
          console.debug('Open new window with url ', url);
          await clients.openWindow(url);
        }
      } catch(e) {
        console.error(e);
      }
    }
    resolve();
  }));
});

self.addEventListener('notificationclose', (event) => {
  event.waitUntil(new Promise(async (resolve) => {
    await refreshBadge();
    await updateNotification(event.notification.data.notificationId, 'markRead');
    resolve();
  }));
});

async function updateNotification(notificationId, action) {
  await fetch(`/pwa/rest/notifications/${notificationId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: `action=${action}`
  });
}

async function refreshBadge() {
  if (navigator.setAppBadge) {
    const notifications = await self.registration.getNotifications();
    navigator?.setAppBadge?.(notifications?.length);
  }
}