<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

-->
<template>
  <v-card
    class="d-flex align-center no-border-radius border-box-sizing px-4 layout-sticky-top-bar"
    height="57"
    width="100vw"
    max-width="100%"
    flat>
    <div class="d-flex flex-row align-center full-height full-width">
      <div class="d-inline-flex ms-4">
        <v-list-item-avatar 
          height="36"
          max-width="100"
          min-width="auto"
          width="auto"
          class="ma-0"
          tile>
          <v-img
            src="/portal/rest/v1/platform/branding/logo"
            height="36px"
            contain />
        </v-list-item-avatar>
        <div class="align-self-center brandingContainer ms-4">
          <div class="logoTitle text-body menu-text-color font-weight-bold">
            {{ companyName }}
          </div>
        </div>
      </div>
      <v-spacer />
      <div class="flex-grow-0 flex-shrink-1 overflow-hidden hidden-sm-and-down">
        <div v-if="online" class="text-truncate success--text">
          {{ $t('OfflineApp.pwa.onlineInfo') }}
        </div>
        <div v-else class="text-truncate error--text">
          {{ $t('OfflineApp.pwa.offlineInfo') }}
        </div>
      </div>
    </div>
  </v-card>
</template>
<script>
export default {
  data: () => ({
    branding: null,
    online: false,
  }),
  computed: {
    companyName() {
      return this.branding?.companyName;
    },
  },
  watch: {
    online: {
      immediate: true,
      handler() {
        if (this.online) {
          document.dispatchEvent(new CustomEvent('alert-message', {detail: {
            alertType: 'success',
            alertMessage: this.$t('OfflineApp.pwa.siteConnectionEstablished'),
            alertTimeout: 5000000,
            alertLinkCallback: this.reload,
            alertLinkTooltip: this.$t('OfflineApp.pwa.reload'),
            alertLinkIcon: 'fa-sync',
          }}));
        } else {
          document.dispatchEvent(new CustomEvent('alert-message', {detail: {
            alertType: 'info',
            alertMessage: this.$t('OfflineApp.pwa.noSiteConnection'),
            alertTimeout: 5000000,
          }}));
        }
      },
    },
  },
  created() {
    this.init();
  },
  methods: {
    reload() {
      window.location.reload();
    },
    async init() {
      this.branding = await fetch('/portal/rest/v1/platform/branding?type=json')
        .then(resp => resp?.ok && resp.json());
      setInterval(this.checkOnline, 3000);
      this.checkOnline();
    },
    async checkOnline() {
      try {
        const resp = await fetch('/', {
          method: 'HEAD',
          redirect: 'manual',
        });
        this.online = resp.status < 400;
      } catch {
        this.online = false;
      }
    },
  }
};
</script>
