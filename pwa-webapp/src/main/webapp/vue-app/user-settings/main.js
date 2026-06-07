/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import './initComponents.js';
// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('UserSettingPwa');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

//getting language of user
const lang = eXo?.env?.portal?.language || 'en';

//should expose the locale ressources as REST API 
const url = `/social/i18n/locale.portlet.social.UserSettings?lang=${lang}`;

let deferredPrompt;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
});

const appId = 'UserSettingPwa';
document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    const appElement = document.createElement('div');
    appElement.id = appId;
    return Vue.createApp({
      data: {
        deferredPrompt,
      },
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      },
      template: `<user-setting-pwa id="${appId}" v-cacheable />`,
      i18n,
      vuetify: Vue.prototype.vuetifyOptions,
    }, appElement, 'User Settings PWA');
  }).finally(() => {
    Vue.prototype.$utils.includeExtensions('PWAUserSettings');
  });
}

export async function openPushNotificationDelayHelpDrawer() {
  const id = 'pwa-pushNotification-delay-drawer';
  const drawerAppElement = getDrawerAppElement(id);
  const i18n = await exoi18n.loadLanguageAsync(lang, `/social/i18n/locale.portlet.social.UserSettings?lang=${eXo.env.portal.language}`);
  new Vue({
    i18n,
    vuetify: Vue.prototype.vuetifyOptions,
    template: `
      <v-app id="${id}">
        <user-setting-pwa-push-delivery-help-drawer
          ref="drawer"
          allow-hide />
      </v-app>
    `,
    mounted() {
      document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      this.$refs.drawer.open();
      document.dispatchEvent(new CustomEvent('close-alert-message'));
    },
  }).$mount(drawerAppElement);
}

function getDrawerAppElement(id) {
  let drawerAppElement = document.querySelector(`#${id}`);
  if (!drawerAppElement) {
    let parentElement = document.querySelector('#vuetify-apps') || document.querySelector('#body-end-container');
    if (!parentElement) {
      parentElement = document.createElement('div');
      parentElement.classList.add('VuetifyApp');
      document.body.appendChild(parentElement);
    }
    drawerAppElement = document.createElement('div');
    drawerAppElement.id = id;
    drawerAppElement.class = 'v-application v-application--is-ltr transparent theme--light';
    parentElement.appendChild(drawerAppElement);
  }
  return drawerAppElement;
}
