/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

(function(exoi18n) {
  if (!isPwaDisplay()
    && eXo.env.portal.pwaEnabled
    && eXo.env.portal.userName) {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      window.deferredPwaPrompt = e;
      unsubscribe();
      document.dispatchEvent(new CustomEvent('pwa-beforeinstallprompt'));
      if (window.localStorage
          && !window.localStorage.getItem(`pwa.suggested-${eXo.env.portal.userName}`)) {
        window.deferredPwaPromptTimeout = window.setTimeout(async () => {
          const i18n = await exoi18n.loadLanguageAsync(eXo.env.portal.language, `/social/i18n/locale.portlet.Portlets?lang=${eXo.env.portal.language}`);
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest'],
            alertLinkText: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest.install'],
            alertType: 'info',
            alertDismissCallback: () => {
              window.localStorage.setItem(`pwa.suggested-${eXo.env.portal.userName}`, 'true');
            },
            alertLinkCallback: () => {
              document.dispatchEvent(new CustomEvent('close-alert-message'));
              window.deferredPwaPrompt.prompt()
                .then(() => window.deferredPwaPrompt.userChoice)
                .then(userChoice => {
                  const outcome = userChoice?.outcome;
                  if (outcome === 'accepted') {
                    window.deferredPwaPrompt = null;
                    initSubscription();
                  }
                  window.localStorage.setItem(`pwa.suggested-${eXo.env.portal.userName}`, 'true');
                });
            },
          }}));
        }, 5000);
      }
    });
  } else if (!eXo.env.portal.pwaEnabled) {
    unsubscribe();
  }

  // Once installed clear timeout and close alert
  window.addEventListener('appinstalled', () => {
    if (window.deferredPwaPromptTimeout) {
      window.clearTimeout(window.deferredPwaPromptTimeout);
    }
    document.dispatchEvent(new CustomEvent('closet-alert-message'));
    initSubscription();
  });

  async function init() {
    if (isPwaDisplay()
      && eXo.env.portal.userName
      && eXo.env.portal.pwaEnabled
      && 'serviceWorker' in navigator)  {
      initSubscription();
    }
  }

  async function initSubscription() {
    try {
      let registration = await navigator.serviceWorker.getRegistration();
      if (!registration) {
        registration = await navigator.serviceWorker.register('/pwa/rest/service-worker',{
            scope: '/',
        });
      }
      if (eXo.developing
        || (
          window.localStorage.getItem('pwa.service-worker.version')
          && window.localStorage.getItem('pwa.service-worker.version') !== eXo.env.client.assetsVersion)) {
        await registration.update();
        window.localStorage.setItem('pwa.service-worker.version', eXo.env.client.assetsVersion);
      }
      await navigator.serviceWorker.ready;
      navigator.serviceWorker.addEventListener('message', (event) => {
        if (event?.data?.action === 'redirect-path'
           && event.data.url?.includes(window.location.origin)) {
          window.location.href = event.data.url;
        }
      });

      if (!('PushManager' in window)) {
        return;
      }

      if (!('Notification' in window)) {
        return;
      } else if (Notification.permission === 'denied'
        && window.localStorage.getItem(`pwa.notification.suggested-${eXo.env.portal.userName}`)) {
        return;
      }

      if (Notification.permission === 'granted') {
        subscribe(registration);
      } else {
        const i18n = await exoi18n.loadLanguageAsync(eXo.env.portal.language, `/social/i18n/locale.portlet.Portlets?lang=${eXo.env.portal.language}`);
        window.setTimeout(() => {
          if (!eXo.env.portal.pwaEnabled) {
            return;
          }
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.allowNotifications'],
            alertLinkText: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.allowNotifications.chooseOption'],
            alertType: 'info',
            alertDismissCallback: () => {
              window.localStorage.setItem(`pwa.notification.suggested-${eXo.env.portal.userName}`, 'true');
            },
            alertLinkCallback: async () => {
              document.dispatchEvent(new CustomEvent('close-alert-message'));
              window.localStorage.setItem(`pwa.notification.suggested-${eXo.env.portal.userName}`, 'true');
              // Get notification permission from user
              const permission = await Notification.requestPermission();
              if (permission === 'granted') {
                subscribe(registration, true);
              }
            },
          }}));
        }, 3000);
      }
    } catch (e) {
      console.error('Error registering service worker', e);
    }
  }

  async function subscribe(registration, forceRegister) {
    let subscription = !forceRegister && await registration.pushManager.getSubscription() || null;
    let isNew = !subscription?.endpoint;
    if (isNew) {
      subscription = await registration.pushManager.subscribe({
        applicationServerKey: eXo.env.portal.pwaPushPublicKey,
        userVisibleOnly: true
      });
    }
    if (isNew
      || subscription.endpoint !== window.localStorage.getItem(`pwa.notification.endpoint-${eXo.env.portal.userName}`)) {
      const key = subscription?.getKey?.('p256dh') || '';
      const auth = subscription?.getKey?.('auth') || '';
      await fetch('/pwa/rest/subscriptions', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: getSubscriptionId(),
          endpoint: subscription.endpoint,
          key: key && btoa(String.fromCharCode.apply(null, new Uint8Array(key))) || '',
          auth: auth && btoa(String.fromCharCode.apply(null, new Uint8Array(auth))) || ''
        }),
      })
        .then(() => window.localStorage.setItem(`pwa.notification.endpoint-${eXo.env.portal.userName}`, subscription.endpoint));
    }
  }

  async function unsubscribe() {
    const registration = await navigator?.serviceWorker?.getRegistration?.();
    if (registration) {
      await registration.unregister();
      const subscriptionId = window.localStorage.getItem(`pwa.notification.subscription.id-${eXo.env.portal.userName}`);
      if (subscriptionId) {
        await fetch('/pwa/rest/subscriptions', {
          method: 'DELETE',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            id: subscriptionId,
          }),
        })
          .finally(() => {
            window.localStorage.removeItem(`pwa.notification.subscription.id-${eXo.env.portal.userName}`);
            window.localStorage.removeItem(`pwa.notification.endpoint-${eXo.env.portal.userName}`);
          });
      }
    }
  }

  function getSubscriptionId() {
    let subscriptionId = window.localStorage.getItem(`pwa.notification.subscription.id-${eXo.env.portal.userName}`);
    if (!subscriptionId) {
      const id = eXo.env.portal.userName + parseInt(Math.random() * Number.MAX_SAFE_INTEGER) + navigator.userAgent;
      subscriptionId = 0;
      let i = 0;
      while (i < id.length) {
        subscriptionId = (subscriptionId << 5) - subscriptionId + id.charCodeAt(i++) | 0;
      }
      window.localStorage.setItem(`pwa.notification.subscription.id-${eXo.env.portal.userName}`, subscriptionId);
    }
    return subscriptionId;
  }

  function isPwaDisplay() {
    return window?.matchMedia('(display-mode: tabbed)')?.matches
      || window?.matchMedia('(display-mode: standalone)')?.matches;
  }

  return {
    init,
  };
})(exoi18n);
