<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

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
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    right>
    <template #title>
      {{ $t('UserSettings.pwa.notification.deliveryStatus.drawer.title') }}
    </template>
    <template #content>
      <v-card class="pa-4" flat>
        <div class="font-weight-bold">{{ $t('UserSettings.pwa.notification.deliveryStatus.drawer.summary') }}</div>
        <div v-sanitized-html="body" class="mt-4"></div>
      </v-card>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          v-if="allowHide"
          color="error"
          elevation="0"
          class="me-2"
          outlined
          @click="hide">
          {{ $t('UserSettings.pwa.notification.deliveryStatus.drawer.hide') }}
        </v-btn>
        <v-btn
          v-else
          class="btn me-2"
          @click="close">
          {{ $t('UserSettings.pwa.notification.deliveryStatus.drawer.cancel') }}
        </v-btn>
        <v-btn
          :loading="loading"
          color="primary"
          elevation="0"
          outlined
          @click="reset">
          {{ $t('UserSettings.pwa.notification.deliveryStatus.drawer.reset') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    allowHide: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    drawer: false,
    loading: false,
  }),
  computed: {
    body() {
      return this.$t('UserSettings.pwa.notification.deliveryStatus.drawer.body').replaceAll('\\n', '<br>');
    },
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
    hide() {
      window.localStorage.setItem(`pwa.notification.pushNotificationExcessiveDelaySuggested-${eXo.env.portal.userName}`, 'true');
      this.$emit('hide');
      this.$refs.drawer.close();
    },
    close() {
      this.$emit('close');
      this.$refs.drawer.close();
    },
    async reset() {
      this.loading = true;
      try {
        await pwa.resetPushDeliveryDelayStatus();
        this.$emit('reset');
        this.$refs.drawer.close();
      } finally {
        window.setTimeout(() => this.loading = false, 500);
      }
    },
  },
};
</script>
