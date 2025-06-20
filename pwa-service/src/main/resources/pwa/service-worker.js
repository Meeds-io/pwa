const offlineVersion = '@assets-version@';
const cacheName = `offline`;
const lang = `@lang@`;
const offlineUrl = `/pwa/html/offline.html?v=${offlineVersion}`;
const manifestUrl = '/pwa/rest/manifest';
const serviceWorkerUrl = '/pwa/rest/service-worker';
const offlineAssets = [
  offlineUrl,
  manifestUrl,
  serviceWorkerUrl,
  '/portal/rest/v1/platform/branding/favicon',
  '/platform-ui/skin/fonts/flUhRq6tzZclQEJ-Vdg-IuiaDsNc.woff2',
  '/platform-ui/skin/fonts/fa-solid-900.woff2',
  '/platform-ui/skin/fonts/fa-regular-400.woff2',
  '/platform-ui/skin/fonts/materialdesignicons-webfont.woff2?v=5.9.55',
  `/portal/rest/v1/platform/branding/css`,
  `/platform-ui/skin/css/core.css?orientation=LT&minify=true&hash=1`,
  `/platform-ui/skin/css/vuetify-all.css?orientation=LT&minify=true&hash=2`,
  `/social/js/bootstrap.js?hash=0&scope=SHARED&minify=true`,
  `/social/js/vueGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/js/baseGRP.js?hash=0&scope=GROUP&minify=true`,
  `/social/js/purifyGRP.js?hash=0&scope=GROUP&minify=true`,
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

const activate = async () => {
  if (self?.registration?.navigationPreload) {
    await self.registration.navigationPreload.enable();
  }
  await populateCache();
};

const populateCacheEntry = async (url) => {
  let fallbackResponse;
  if (url === manifestUrl || url === serviceWorkerUrl) {
    fallbackResponse = await fetch(url);
  } else {
    fallbackResponse = await fetch(`${url}${url.includes('/i18n/') ? `?lang=${lang}` : ''}${url.includes('?') ? '&' : '?'}v=offline-v${offlineVersion}`);
  }
  await putInCache(url, fallbackResponse.clone());
};

const populateCache = async () => {
  if (!await caches.has(cacheName)) {
    await Promise.all(offlineAssets.map(async url => populateCacheEntry(url)));
    await setCacheVersion();
  } else {
    await checkCache();
  }
};

const checkCache = async () => {
  const version = await getCacheVersion();
  if (version !== getCacheVersionValue()) {
    caches.delete(cacheName);
  }
};

async function putInCache(request, response) {
  const cache = await caches.open(cacheName);
  await cache.put(request, response);
};

const requestWithFallback = async ({ request }) => {
  let response;
  const assetUrl = offlineAssets.find(url => request.url?.includes?.(url));
  try {
    response = await fetch(request);
    if (response.headers.get('Content-Type') === 'text/html') {
      await populateCache();
    } else if (assetUrl) {
      await putInCache(assetUrl, response.clone());
    }
    return response;
  } catch (error) {
    const url = request.destination === 'document' ? offlineUrl : assetUrl;
    if (url) {
      const cache = await caches.open(cacheName);
      return await cache.match(url);
    } else {
      throw error;
    }
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
});

self.addEventListener('activate', event => {
  event.waitUntil(activate());
});

self.addEventListener('fetch', event => {
  event.respondWith(requestWithFallback(event));
});

self.addEventListener('push', event => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.text?.() || {};
    const action = data.split(':')[1]
    event.waitUntil(new Promise(async (resolve, reject) => {
      try {
        if (action === 'open') {
          const notificationId = data.split(':')[0]
          const webNotification = await fetch(`/pwa/rest/notifications/${notificationId}`, {
            method: 'GET',
            credentials: 'include',
          }).then(resp => resp.ok && resp.json());
          if (webNotification) {
            const title = webNotification.title || '';
            delete webNotification.title;
            webNotification.icon = webNotification.icon || webNotification.image || self.location.origin + '/pwa/rest/manifest/smallIcon?sizes=72x72';
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
              delete webNotification.badge;
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
        resolve();
      } catch (e) {
        reject(e);
      }
    }));
  }
});

self.addEventListener('notificationclick', event => {
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

self.addEventListener('notificationclose', event => {
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