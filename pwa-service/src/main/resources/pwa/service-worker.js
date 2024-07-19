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
        const notification = notifications.find(notification => notification?.data?.notificationId === notificationId);
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
          delete webNotification.renotify;
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
        if (!webNotification.vibrate) {
          delete webNotification.vibrate;
        }
        if (!webNotification.badge) {
          webNotification.badge = webNotification.icon;
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
    const notificationId = event?.notification?.data?.notificationId || event?.notification?.tag;
    try {
      if (event.action) {
        await updateNotification(notificationId, event.action);
      } else if (clients && 'openWindow' in clients && 'matchAll' in clients) {
        const windowClients = await clients.matchAll({
          type: 'window',
          includeUncontrolled: true,
        });
        let matchingClient = null;
        let i = 0;
        while (!matchingClient && i < windowClients.length) {
          if (!windowClients[i].url.replace(self.location.origin, '').includes('editor')) {
            matchingClient = windowClients[i];
          } else {
            i++;
          }
        }

        if (matchingClient?.navigate && matchingClient?.focus) {
          try {
            await matchingClient.focus();
            try {
              await matchingClient.navigate(url);
            } catch(e) {
              matchingClient.postMessage({
                action: 'redirect-path',
                url,
              });
            }
          } catch(e) {
            await clients.openWindow(url);
          }
        } else {
          await clients.openWindow(url);
        }
      }
    } catch(e) {
      console.error(e);
    } finally {
      await handleClose(notificationId);
      resolve();
    }
  }));
});

self.addEventListener('notificationclose', (event) => {
  const notificationId = event?.notification?.data?.notificationId || event?.notification?.tag;
  if (notificationId) {
    event.waitUntil(new Promise(async (resolve) => {
      try {
        await handleClose(notificationId);
      } finally {
        resolve();
      }
    }));
  }
});

async function handleClose(notificationId) {
  try {
    await updateNotification(notificationId, 'markRead');
  } catch(e) {
    console.error(e);
  }
  try {
    await refreshBadge();
  } catch(e) {
    console.error(e);
  }
}

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
    if (notifications?.length) {
      await navigator?.setAppBadge?.(notifications.length);
    } else {
      await navigator?.clearAppBadge?.();
    }
  }
}