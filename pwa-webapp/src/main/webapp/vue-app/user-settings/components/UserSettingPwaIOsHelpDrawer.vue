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
      {{ $t('UserSettings.pwa.ios.drawer.title') }}
    </template>
    <template #content>
      <v-card class="pa-4" flat>
        <div class="font-weight-bold">{{ $t('UserSettings.pwa.ios.drawer.summary') }}</div>
        <div v-sanitized-html="body" class="mt-4"></div>
        <div class="text-header my-4">{{ $t('UserSettings.pwa.ios.drawer.header') }}</div>
        <div v-sanitized-html="step" class="my-4"></div>
        <v-img
          :src="imageSrc"
          max-width="100%"
          width="213"
          class="mx-auto mt-4"
          contain />
      </v-card>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
  }),
  computed: {
    body() {
      return this.$t('UserSettings.pwa.ios.drawer.body').replaceAll('\\n', '<br>');
    },
    step() {
      return this.$t('UserSettings.pwa.ios.drawer.step').replaceAll('\\n', '<br>');
    },
    imageSrc() {
      return eXo.env.portal.language.startsWith('fr') ? '/pwa/images/pwa-help-iOS-fr.webp' : '/pwa/images/pwa-help-iOS.webp';
    },
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
  },
};
</script>
