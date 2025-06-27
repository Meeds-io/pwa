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
    min-width="100vw"
    width="100vw"
    flat>
    <div class="d-flex flex-row full-height">
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
    </div>
  </v-card>
</template>
<script>
export default {
  data: () => ({
    branding: null,
  }),
  computed: {
    companyName() {
      return this.branding?.companyName;
    },
  },
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.branding = await fetch('/portal/rest/v1/platform/branding?type=json')
        .then(resp => resp?.ok && resp.json());
    },
  }
};
</script>
