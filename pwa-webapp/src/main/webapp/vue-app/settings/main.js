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
import './services.js';

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('PwaSettings');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

//getting language of user
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const urls = [
  `/social-portlet/i18n/locale.portlet.GeneralSettings?lang=${lang}`,
  `/pwa/i18n/locale.portlet.PwaSettings?lang=${lang}`
];
const appId = 'PwaSettings';

document.dispatchEvent(new CustomEvent('displayTopBarLoading'));

export function init() {
  exoi18n.loadLanguageAsync(lang, urls).then(i18n => {
    Vue.createApp({
      data: {
        selectedTab: null,
        loading: false,
      },
      computed: {
        isMobile() {
          return this.$vuetify.breakpoint.mobile;
        },
      },
      watch: {
        loading() {
          if (this.loading) {
            document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
          } else {
            document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
          }
        },
        selectedTab() {
          if (this.selectedTab === 'pwa') {
            if (window.location.hash !== '#pwa') {
              window.location.hash = '#pwa';
            }
          } else if (!this.selectedTab) {
            window.history.replaceState('', window.document.title, window.location.href.split('#')[0]);
          }
        },
      },
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      },
      template: `<pwa-settings id="${appId}" />`,
      i18n,
      vuetify: Vue.prototype.vuetifyOptions,
    }, `#${appId}`, 'PWA Settings');
  });
}
