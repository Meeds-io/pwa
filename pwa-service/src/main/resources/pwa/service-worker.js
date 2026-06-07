const offlineVersion = '@assets-version@';
const cacheName = `offline`;
const lang = `@lang@`;
const offlineModeEnabled = @pwa.offline.enabled@;
const offlineUrl = `/pwa/html/offline.html?v=${offlineVersion}`;
const manifestUrl = '/pwa/rest/manifest';
const serviceWorkerUrl = '/pwa/rest/service-worker';
const offlineAssets = [
  offlineUrl,
  manifestUrl,
  serviceWorkerUrl,
  '/portal/rest/v1/platform/branding?type=json',
  '/portal/rest/v1/platform/branding/logo',
  '/portal/rest/v1/platform/branding/favicon',
  `/portal/rest/v1/platform/branding/css`,
  '/platform-ui/skin/fonts/flUhRq6tzZclQEJ-Vdg-IuiaDsNc.woff2',
  '/platform-ui/skin/fonts/fa-solid-900.woff2',
  '/platform-ui/skin/fonts/fa-regular-400.woff2',
  '/platform-ui/skin/fonts/materialdesignicons-webfont.woff2?v=5.9.55',
  `/platform-ui/skin/css/core.css?orientation=LT&minify=true&hash=1`,
  `/platform-ui/skin/css/vuetify-all.css?orientation=LT&minify=true&hash=2`,
  `/social/js/bootstrap.js?hash=0&scope=SHARED&minify=true`,
  `/social/js/vueGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/js/baseGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/js/purifyGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/js/applicationToolbarComponent.js?hash=0&scope=SHARED&minify=true`,
  `/cometd/js/cometdGRP.js?hash=0&scope=GROUP&minify=true`,
  `/pwa/js/pwaOfflineGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/i18n/locale.portlet.Portlets`,
  `/social/i18n/locale.social.Webui`,
  `/social/i18n/locale.commons.Commons`,
  `/social/i18n/locale.portlet.social.UserPopup`,
  `/social/i18n/locale.portlet.social.SpacesListApplication`,
  `/social/i18n/locale.portal`,
  `/pwa/i18n/locale.portlet.OfflineApplication`,
];

const pushDeviceDbName = 'pwa-push-device';
const pushDeviceStoreName = 'secrets';
const pushDeviceSecretKeyPrefix = 'push-device-secret-';
const pushAuthScheme = 'PWA-Notification';

const checkCache = async () => {
  const version = await getCacheVersion();
  if (version !== getCacheVersionValue()) {
    await caches.delete(cacheName);
    await populateCache();
  }
};

async function putInCache(request, response) {
  const cache = await caches.open(cacheName);
  await cache.put(request, response);
};

const populateCacheEntry = async (url) => {
  let fallbackResponse;
  if (url === manifestUrl || url === serviceWorkerUrl) {
    fallbackResponse = await fetch(url);
  } else if (url.includes('/i18n/')) {
    fallbackResponse = await fetch(`${url}?lang=${lang}&v=offline-v${offlineVersion}`);
  } else {
    fallbackResponse = await fetch(`${url}${url.includes('?') ? '&' : '?'}v=offline-v${offlineVersion}`);
  }
  await putInCache(url, fallbackResponse.clone());
};

const populateCache = async () => {
  if (offlineModeEnabled) {
    if (!await caches.has(cacheName)) {
      await Promise.all(offlineAssets.map(async url => populateCacheEntry(url)));
      await setCacheVersion();
    } else {
      await checkCache();
    }
  }
};

const activateNavigationPreload = async () => {
  if (self?.registration?.navigationPreload) {
    await self.registration.navigationPreload.enable();
  }
};

const requestWithFallback = async (event) => {
  let response;
  const request = event.request;
  const assetUrl = offlineAssets.find(url => request.url?.includes?.(url));
  try {
    response = await event.preloadResponse;
    if (!response) {
      response = await fetch(request);
    }
    if (response.status >= 400) {
      throw new Error();
    } else if (response.headers.get('Content-Type') === 'text/html') {
      await populateCache();
    } else if (assetUrl) {
      await putInCache(assetUrl, response.clone());
    }
    return response;
  } catch (error) {
    const url = request.destination === 'document' ? offlineUrl : assetUrl;
    if (url && await checkOffline()) {
      const cache = await caches.open(cacheName);
      return await cache.match(url);
    } else if (response) {
      return response;
    } else {
      throw error;
    }
  }
};

async function checkOffline() {
  try {
    const response = await fetch('/', {
      method: 'HEAD',
      redirect: 'manual',
    });
    return response.status >= 400;
  } catch {
    return true;
  }
};

async function getCacheVersion() {
  const cache = await caches.open(cacheName);
  const resp = await cache.match('version');
  const version = await resp?.text?.();
  return version;
};

async function setCacheVersion() {
  const cache = await caches.open(cacheName);
  await cache.put('version', new Response(getCacheVersionValue()));
};

function getCacheVersionValue() {
  return `${offlineVersion}-${lang}`;
};

self.addEventListener('install', event => {
  event.waitUntil(populateCache());
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(Promise.all([
    clients.claim(),
    activateNavigationPreload(),
  ]));
});

if (offlineModeEnabled) {
  self.addEventListener('fetch', event => {
    event.respondWith(requestWithFallback(event));
  });
}

self.addEventListener('message', event => {
  if (event?.data?.action === 'set-push-device-secret'
      && event.data.subscriptionId
      && event.data.pushDeviceSecret) {
    event.waitUntil(setPushDeviceSecret(event.data.subscriptionId, event.data.pushDeviceSecret));
  }
});

self.addEventListener('push', event => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const params = data.split(':');
    const notificationType = params[0];
    if(!notificationType || notificationType === 'WEB_NOTIFICATION') {
      const action = params[2];
      event.waitUntil(new Promise(async (resolve, reject) => {
        try {
          if (action === 'open') {
            const notificationId = params[1];
            const notificationAccessToken = params[3];
            const subscriptionId = params[4];
            const sentAt = Number(params[5]);
            const receivedAt = Date.now();
            await reportPushDeliveryDelay(notificationId, notificationAccessToken, subscriptionId, sentAt, receivedAt);
            let webNotification = await getWebNotification(notificationId, notificationAccessToken, subscriptionId);
            if (!webNotification) {
              webNotification = getFallbackNotification(notificationId);
            }
            const title = webNotification.title || getFallbackNotificationTitle();
            webNotification.type = 'WEB_NOTIFICATION';
            prepareNotificationToSend(notificationId, webNotification, notificationAccessToken, subscriptionId);
            await self.registration.showNotification(title, webNotification);
            await refreshBadge();
          }
          resolve();
        } catch (e) {
          reject(e);
        }
      }));
    }
  }
});

self.addEventListener('notificationclick', event => {
  const url = event.notification.data.url;
  const notificationType = event?.notification?.data?.type;
  event.waitUntil(new Promise(async (resolve) => {
    event.notification.close();
    const notificationId = event?.notification?.data?.notificationId || event?.notification?.tag;
    const notificationAccessToken = event?.notification?.data?.accessToken;
    const subscriptionId = event?.notification?.data?.subscriptionId;
    try {
      if (event.action) {
        if(!notificationType || notificationType === 'WEB_NOTIFICATION') {
          await updateNotification(notificationId, event.action, notificationAccessToken, subscriptionId);
        }
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
      await markAsRead(notificationId, notificationAccessToken, subscriptionId);
      resolve();
    }
  }));
});

self.addEventListener('notificationclose', event => {
  event.waitUntil(refreshBadge);
});

async function markAsRead(notificationId, notificationAccessToken, subscriptionId) {
  try {
    await updateNotification(notificationId, 'markRead', notificationAccessToken, subscriptionId);
  } catch(e) {
    console.error(e);
  }
  try {
    await refreshBadge();
  } catch(e) {
    console.error(e);
  }
}


async function reportPushDeliveryDelay(notificationId, notificationAccessToken, subscriptionId, sentAt, receivedAt) {
  if (!sentAt || !receivedAt) {
    return;
  }
  const authorizationHeader = await getPushAuthorizationHeader(notificationId, notificationAccessToken, subscriptionId);
  if (!authorizationHeader) {
    return;
  }
  try {
    await fetch(`/pwa/rest/notifications/${notificationId}/push/delivery-delay`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Authorization': authorizationHeader,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: `sentAt=${encodeURIComponent(sentAt)}&receivedAt=${encodeURIComponent(receivedAt)}`,
    });
  } catch (e) {
    console.error(e);
  }
}

async function updateNotification(notificationId, action, notificationAccessToken, subscriptionId) {
  const authorizationHeader = await getPushAuthorizationHeader(notificationId, notificationAccessToken, subscriptionId);
  if (authorizationHeader) {
    const response = await fetch(`/pwa/rest/notifications/${notificationId}/push`, {
      method: 'PATCH',
      credentials: 'omit',
      headers: {
        'Authorization': authorizationHeader,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: `action=${action}`
    });
    if (response.ok) {
      return;
    }
  }
  await fetch(`/pwa/rest/notifications/${notificationId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: `action=${action}`
  });
}

async function getWebNotification(notificationId, notificationAccessToken, subscriptionId) {
  const authorizationHeader = await getPushAuthorizationHeader(notificationId, notificationAccessToken, subscriptionId);
  if (authorizationHeader) {
    const response = await fetch(`/pwa/rest/notifications/${notificationId}/push`, {
      method: 'GET',
      credentials: 'omit',
      headers: {
        'Authorization': authorizationHeader,
      },
    });
    if (response.ok) {
      return response.json();
    }
  }
  return fetch(`/pwa/rest/notifications/${notificationId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => resp.ok && resp.json());
}

async function getPushAuthorizationHeader(notificationId, notificationAccessToken, subscriptionId) {
  if (!notificationAccessToken || !subscriptionId) {
    return null;
  }
  const deviceSecret = await getPushDeviceSecret(subscriptionId);
  if (!deviceSecret) {
    return null;
  }
  const timestamp = Date.now().toString();
  const proof = await hmac(deviceSecret, `${notificationId}:${notificationAccessToken}:${subscriptionId}:${timestamp}`);
  return `${pushAuthScheme} token=${notificationAccessToken},subscriptionId=${subscriptionId},timestamp=${timestamp},proof=${proof}`;
}

async function hmac(secret, payload) {
  const key = await crypto.subtle.importKey(
    'raw',
    base64ToBytes(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(payload));
  return base64UrlEncode(new Uint8Array(signature));
}

function base64UrlEncode(bytes) {
  let value = '';
  bytes.forEach(byte => value += String.fromCharCode(byte));
  return btoa(value)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function base64ToBytes(value) {
  return Uint8Array.from(atob(value), c => c.charCodeAt(0));
}

async function getPushDeviceSecret(subscriptionId) {
  return getPushDeviceStoreValue(`${pushDeviceSecretKeyPrefix}${subscriptionId}`);
}

async function setPushDeviceSecret(subscriptionId, secret) {
  return setPushDeviceStoreValue(`${pushDeviceSecretKeyPrefix}${subscriptionId}`, secret);
}

async function openPushDeviceStore() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(pushDeviceDbName, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(pushDeviceStoreName);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function getPushDeviceStoreValue(key) {
  const db = await openPushDeviceStore();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(pushDeviceStoreName, 'readonly');
    const request = transaction.objectStore(pushDeviceStoreName).get(key);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    transaction.oncomplete = () => db.close();
  });
}

async function setPushDeviceStoreValue(key, value) {
  const db = await openPushDeviceStore();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(pushDeviceStoreName, 'readwrite');
    transaction.objectStore(pushDeviceStoreName).put(value, key);
    transaction.oncomplete = () => {
      db.close();
      resolve();
    };
    transaction.onerror = () => {
      db.close();
      reject(transaction.error);
    };
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

function getFallbackNotification(notificationId) {
  return {
    title: getFallbackNotificationTitle(),
    body: 'Open the app to view this notification.',
    url: '/',
    tag: notificationId,
    requireInteraction: true,
    renotify: true,
  };
}

function getFallbackNotificationTitle() {
  return 'New notification';
}

function prepareNotificationToSend(notificationId, webNotification, notificationAccessToken, subscriptionId) {
  delete webNotification.title;
  webNotification.icon = webNotification.icon || webNotification.image || self.location.origin + '/pwa/rest/manifest/smallIcon?sizes=72x72';
  webNotification.badge = self.location.origin + '/pwa/rest/manifest/monochromeIcon';

  webNotification.data = {
    notificationId,
    url: self.location.origin + (webNotification.url || '/'),
    type: webNotification.type,
    accessToken: notificationAccessToken,
    subscriptionId,
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

  if (!Notification.maxActions || !webNotification.actions) {
    delete webNotification.actions;
  } else if (webNotification.actions.length > Notification.maxActions) {
    webNotification.actions = webNotification.actions.slice(0, Notification.maxActions);
  }
  return webNotification;
}

@service-worker-extensions@
