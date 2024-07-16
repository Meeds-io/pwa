<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io

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
  <v-app>
    <v-main>
      <v-card
        class="px-6 application-body"
        flat>
        <template v-if="intialized">
          <v-expand-transition>
            <v-list-item
              v-if="$root.selectedTab"
              dense
              class="px-0 mb-4">
              <v-list-item-action class="my-auto me-0 ms-n2">
                <v-btn
                  :title="$t('generalSettings.access.backToMain')"
                  size="24"
                  icon
                  @click="close">
                  <v-icon size="18" class="icon-default-color">
                    {{ $vuetify.rtl && 'fa-arrow-right' || 'fa-arrow-left' }}
                  </v-icon>
                </v-btn>
              </v-list-item-action>
              <v-list-item-content>
                <v-list-item-title class="d-flex">
                  <v-card
                    :title="$t('generalSettings.access.backToMain')"
                    class="flex-grow-0 py-1"
                    flat
                    @click="close()">
                    <div class="text-title">
                      <template v-if="$root.selectedTab === 'pwa'">
                        {{ $t('pwaSettings.characteristics') }}
                      </template>
                    </div>
                  </v-card>
                </v-list-item-title>
              </v-list-item-content>
            </v-list-item>
          </v-expand-transition>
        </template>
      </v-card>
      <exo-confirm-dialog
        ref="closeConfirmDialog"
        :title="$t('generalSettings.closeTabConfirmTitle')"
        :message="$t('generalSettings.closeTabConfirmMessage')"
        :ok-label="$t('generalSettings.yes')"
        :cancel-label="$t('generalSettings.no')"
        persistent
        @ok="closeEffectively" />
    </v-main>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    branding: null,
    errorMessage: null,
    intialized: false,
    changed: false,
  }),
  watch: {
    errorMessage() {
      if (this.errorMessage) {
        this.$root.$emit('alert-message', this.$t(this.errorMessage), 'error');
      } else {
        this.$root.$emit('close-alert-message');
      }
    },
  },
  created() {
    if (window.location.hash === '#pwa') {
      this.$root.selectedTab = 'pwa';
    }
  },
  mounted() {
    this.init()
      .then(() => this.$nextTick())
      .finally(() => {
        this.$root.$applicationLoaded();
        this.intialized = true;
      });
  },
  methods: {
    init() {
      this.$root.loading = true;
      return this.initBranding()
        .finally(() => this.$root.loading = false);
    },
    initBranding() {
      return this.$brandingService.getBrandingInformation()
        .then(data => this.branding = data);
    },
    close() {
      if (this.changed) {
        this.$refs.closeConfirmDialog.open();
      } else {
        this.closeEffectively();
      }
    },
    closeEffectively() {
      this.confirmClose = false;
      this.$nextTick().then(() => {
        this.$root.selectedTab = null;
        this.changed = false;
      });
    },
  },
};
</script>
