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
  if (!pwaMode) {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      window.deferredPwaPrompt = e;
      if (window.localStorage && !window.localStorage.getItem('pwa.suggested')) {
        window.deferredPwaPromptTimeout = window.setTimeout(async () => {
          const i18n = await exoi18n.loadLanguageAsync(eXo.env.portal.language, `/social-portlet/i18n/locale.portlet.Portlets?lang=${eXo.env.portal.language}`);
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest'],
            alertLinkText: i18n.messages?.[eXo.env.portal.language]?.['pwa.feature.suggest.install'],
            alertType: 'info',
            alertLinkCallback: async () => {
              document.dispatchEvent(new CustomEvent('close-alert-message'));
              await window.deferredPwaPrompt.prompt();
              const { outcome } = await window.deferredPwaPrompt.userChoice;
              if (outcome === 'accepted') {
                window.deferredPwaPrompt = null;
              }
              window.localStorage.setItem('pwa.suggested', 'true');
            },
          }}));
        }, 5000);
        window.addEventListener('appinstalled', () => {
          window.clearTimeout(window.deferredPwaPromptTimeout);
          document.dispatchEvent(new CustomEvent('closet-alert-message'));
        });
      }
    });
  }
  return {
    init: async () => {
      if (!pwaMode || !('serviceWorker' in navigator))  {
        return;
      }
      try {
        const registration = await navigator.serviceWorker.register('/pwa/rest/service-worker',{
            scope: '/',
        });
        await registration.update();
        await navigator.serviceWorker.ready;

        // Manually set to 'denied' by user
        if (Notification.permission === 'denied'
            || !('PushManager' in window)
            || !('Notification' in window)) {
          return;
        }

        // Get notification permission from user
        const permission = await Notification.requestPermission();
        if (permission !== 'granted') {
          return;
        }

        // TODO: This will be called only when the service worker is activated.
        const subscription = await registration.pushManager.subscribe({
          applicationServerKey: eXo.env.portal.pwaPushPublicKey,
          userVisibleOnly: true
        });
        var key = subscription?.getKey?.('p256dh') || '';
        var auth = subscription?.getKey?.('auth') || '';

        await fetch('/pwa/rest/subscriptions', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            endpoint: subscription.endpoint,
            key: key && btoa(String.fromCharCode.apply(null, new Uint8Array(key))) || '',
            auth: auth && btoa(String.fromCharCode.apply(null, new Uint8Array(auth))) || ''
          }),
        });
      } catch (e) {
        console.error('Error registering service worker', e);
      }
    },
  };
})(exoi18n);
