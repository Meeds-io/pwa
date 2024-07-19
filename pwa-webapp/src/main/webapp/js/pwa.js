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
  const pwaMode = !!window?.matchMedia('(display-mode: standalone)')?.matches;
  if (!pwaMode && eXo.env.portal.userName) {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      window.deferredPwaPrompt = e;
      if (window.localStorage && !window.localStorage.getItem(`pwa.suggested-${eXo.env.portal.userName}`)) {
        window.deferredPwaPromptTimeout = window.setTimeout(async () => {
          const i18n = await exoi18n.loadLanguageAsync(eXo.env.portal.language, `/social-portlet/i18n/locale.portlet.Portlets?lang=${eXo.env.portal.language}`);
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest'],
            alertLinkText: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest.install'],
            alertType: 'info',
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
        // Once installed clear timeout and close alert
        window.addEventListener('appinstalled', () => {
          window.clearTimeout(window.deferredPwaPromptTimeout);
          document.dispatchEvent(new CustomEvent('closet-alert-message'));
        });
      }
    });
  }

  async function init() {
    if (!pwaMode || !('serviceWorker' in navigator) || !eXo.env.portal.userName)  {
      return;
    }
    initSubscription();
  }

  async function initSubscription() {
    try {
      const registration = await navigator.serviceWorker.register('/pwa/rest/service-worker',{
          scope: '/',
      });
      if (eXo.developing
        || (
          window.localStorage.getItem(`pwa.service-worker.version`)
          && window.localStorage.getItem(`pwa.service-worker.version`) !== eXo.env.client.assetsVersion)) {
        await registration.update();
        window.localStorage.setItem('pwa.service-worker.version', eXo.env.client.assetsVersion);
      }
      await navigator.serviceWorker.ready;

      if (!('PushManager' in window)) {
        console.debug('PushManager not supported by browser');
        return;
      }

      if (!('Notification' in window)) {
        console.debug('Notification not supported by browser');
        return;
      } else if (Notification.permission === 'denied'
        && window.localStorage.getItem(`pwa.notification.suggested-${eXo.env.portal.userName}`)) {
        console.debug('Notification permission', permission, ' explicitely denied ignore notifications registration');
        return;
      }

      if (Notification.permission === 'granted') {
        subscribe(registration);
      } else {
        const i18n = await exoi18n.loadLanguageAsync(eXo.env.portal.language, `/social-portlet/i18n/locale.portlet.Portlets?lang=${eXo.env.portal.language}`);
        window.setTimeout(() => {
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.allowNotifications'],
            alertLinkText: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.allowNotifications.allow'],
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
              } else {
                console.debug('Notification permission', permission, 'not granted, thus ignore Push API registration');
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
    console.debug('Subscribe to Push Notifications, isNew: ', isNew, '(old endpoint === new endpoint): ', subscription.endpoint === window.localStorage.getItem(`pwa.notification.endpoint-${eXo.env.portal.userName}`));
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

  return {
    init,
  };
})(exoi18n);
