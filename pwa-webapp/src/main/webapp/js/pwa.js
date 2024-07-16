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

(function() {
  if (!window?.matchMedia('(display-mode: standalone)')?.matches) {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      window.deferredPwaPrompt = e;
      if (window.localStorage && !window.localStorage.getItem('pwa.suggested')) {
        window.deferredPwaPromptTimeout = window.setTimeout(async () => {
          document.dispatchEvent(new CustomEvent('alert-message', {detail:{
            alertMessage: window.vueI18nMessages['pwa.feature.suggest'],
            alertLinkText: window.vueI18nMessages['pwa.feature.suggest.install'],
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
        }, 15000);
        window.addEventListener('appinstalled', () => {
          window.clearTimeout(window.deferredPwaPromptTimeout);
          document.dispatchEvent('closet-alert-message');
        });
      }
    });
  }
  return {
    init: async () => {
      if (window.eXo.env.portal.pwaEnabled
          && window.Notification
          && window.matchMedia?.('(display-mode: standalone)')?.matches
          && window.Notification?.permission !== 'granted') {
        Notification.requestPermission();
      }
    },
  };
})();
