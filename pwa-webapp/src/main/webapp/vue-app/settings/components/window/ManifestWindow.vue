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
  <v-row v-if="manifest" class="ma-0">
    <v-col
      sm="12"
      cols="12"
      class="pa-0">
      <v-switch v-model="manifest.enabled">
        <template #label>
          <help-label
            label="pwaSettings.enablePwa"
            tooltip="pwaSettings.enablePwa.tooltip">
            <template slot="helpContent">
              <p>
                {{ $t('pwaSettings.enablePwa.help1') }}
              </p>
              <p>
                {{ $t('pwaSettings.enablePwa.help2') }}
              </p>
              <p>
                {{ $t('pwaSettings.enablePwa.help3') }}
              </p>
              <p>
                {{ $t('pwaSettings.enablePwa.help4') }}
              </p>
            </template>
          </help-label>
        </template>
      </v-switch>
    </v-col>
    <v-col
      v-if="manifest.enabled"
      md="6"
      cols="12"
      class="pa-0">
      <v-row class="ma-0">
        <v-col
          cols="12"
          class="pa-0">
          <div class="text-header my-4">
            {{ $t('pwaSettings.Name') }}
          </div>
          <v-text-field
            v-model="manifest.name"
            class="border-box-sizing me-0 me-md-8 pa-0"
            outlined />
        </v-col>
        <v-col
          cols="12"
          class="pa-0">
          <div class="text-header my-4">
            {{ $t('pwaSettings.Description') }}
          </div>
          <extended-textarea
            v-model="manifest.description"
            max-length="1500"
            class="border-box-sizing me-0 me-md-8 pa-0" />
        </v-col>
      </v-row>
    </v-col>
    <v-col
      v-if="manifest.enabled"
      md="6"
      cols="12"
      class="pa-0">
      <v-row class="ma-0">
        <v-col
          cols="12"
          class="mt-4 pa-0 text-header">
          {{ $t('pwaSettings.Icons') }}
        </v-col>
        <v-col cols="12" class="pa-0">
          <v-row class="ma-0">
            <v-col
              md="6"
              cols="12"
              class="pa-0">
              <div class="my-4">
                {{ $t('pwaSettings.LargeIcon') }}
              </div>
              <pwa-settings-image-input
                v-model="manifest.largeIconUploadId"
                ref="largeIcon"
                :default-src="manifest.largeIconPath" />
            </v-col>
            <v-col
              md="6"
              cols="12"
              class="pa-0">
              <div class="my-4">
                {{ $t('pwaSettings.SmallIcon') }}
              </div>
              <pwa-settings-image-input
                v-model="manifest.smallIconUploadId"
                ref="smallIcon"
                :default-src="manifest.smallIconPath" />
            </v-col>
          </v-row>
        </v-col>
        <v-col
          cols="12"
          class="mt-8 pa-0 text-header">
          {{ $t('pwaSettings.Colors') }}
        </v-col>
        <v-col cols="12" class="pa-0">
          <v-row class="ma-0">
            <v-col
              md="6"
              cols="12"
              class="pa-0">
              <div class="mt-4 mb-1">
                {{ $t('pwaSettings.BackgroundColor') }}
              </div>
              <div class="width-fit-content">
                <pwa-settings-color-picker
                  v-model="manifest.backgroundColor" />
              </div>
            </v-col>
            <v-col
              md="6"
              cols="12"
              class="pa-0">
              <div class="mt-4 mb-1">
                {{ $t('pwaSettings.ThemeColor') }}
              </div>
              <div class="width-fit-content">
                <pwa-settings-color-picker
                  v-model="manifest.themeColor" />
              </div>
            </v-col>
          </v-row>
        </v-col>
      </v-row>
    </v-col>
    <v-col
      cols="12"
      class="mt-8 mx-0 px-0">
      <div class="d-flex justify-end pb-5">
        <v-btn
          :aria-label="$t('generalSettings.cancel')"
          :disabled="loading"
          class="btn cancel-button me-4"
          elevation="0"
          @click="$emit('close')">
          <span class="text-none">
            {{ $t('generalSettings.cancel') }}
          </span>
        </v-btn>
        <v-btn
          :aria-label="$t('generalSettings.apply')"
          :disabled="!validForm"
          :loading="loading"
          color="primary"
          class="btn btn-primary register-button"
          elevation="0"
          @click="save">
          <span class="text-capitalize">
            {{ $t('generalSettings.apply') }}
          </span>
        </v-btn>
      </div>
    </v-col>
  </v-row>
</template>
<script>
export default {
  props: {
    branding: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    manifest: null,
    originalManifest: null,
    errorMessage: null,
    loading: false,
  }),
  computed: {
    enabled() {
      return this.manifest?.enabled;
    },
    name() {
      return this.manifest?.name;
    },
    description() {
      return this.manifest?.description;
    },
    themeColor() {
      return this.manifest?.themeColor;
    },
    backgroundColor() {
      return this.manifest?.backgroundColor;
    },
    smallIconUploadId() {
      return this.manifest?.smallIconUploadId;
    },
    largeIconUploadId() {
      return this.manifest?.largeIconUploadId;
    },
    validForm() {
      return this.changed && this.isValidForm;
    },
    isValidForm() {
      return this.name?.length
          && this.description?.length
          && this.manifest?.themeColor?.length
          && this.manifest?.backgroundColor?.length;
    },
    changed() {
      return this.manifest && JSON.stringify(this.manifest) !== JSON.stringify(this.originalManifest);
    },
  },
  watch: {
    errorMessage() {
      if (this.errorMessage) {
        this.$root.$emit('alert-message', this.$te(this.errorMessage) ? this.$t(this.errorMessage) : this.$t('generalSettings.savingError'), 'error');
      } else {
        this.$root.$emit('close-alert-message');
      }
    },
    changed() {
      this.$emit('changed', this.changed);
    },
  },  
  created() {
    this.init();
  },
  methods: {
    init() {
      this.loading = true;
      return this.$pwaManifestService.getManifest()
        .then(manifest => {
          if (manifest) {
            this.manifest = {
              enabled: true,
              name: manifest.name,
              description: manifest.description,
              largeIconPath: manifest.icons[0].src,
              smallIconPath: manifest.icons[manifest.icons.length - 1].src,
              themeColor: manifest.theme_color,
              backgroundColor: manifest.background_color,
              smallIconUploadId: null,
              largeIconUploadId: null,
            };
            this.originalManifest = JSON.parse(JSON.stringify(this.manifest));
          } else {
            this.manifest = {
              enabled: false,
              name: this.branding?.companyName,
              description: this.$te('meeds.pwa.manifest.description') ? this.$t('meeds.pwa.manifest.description') : this.branding?.companyName,
              largeIconPath: '/pwa/rest/manifest/largeIcon',
              smallIconPath: '/pwa/rest/manifest/smallIcon',
              themeColor: this.branding?.themeStyle?.primaryColor,
              backgroundColor: this.branding?.themeStyle?.primaryColor,
              smallIconUploadId: null,
              largeIconUploadId: null,
            };
            this.originalManifest = JSON.parse(JSON.stringify(this.manifest));
          }
          eXo.env.portal.pwaEnabled = this.manifest.enabled;
        })
        .finally(() => this.loading = false);
    },
    reset() {
      this.$refs?.smallIcon?.reset();
      this.$refs?.largeIcon?.reset();
    },
    save() {
      const manifestUpdate = {
        enabled: this.enabled,
        name: this.name,
        description: this.description,
        themeColor: this.themeColor,
        backgroundColor: this.backgroundColor,
        smallIconUploadId: this.smallIconUploadId,
        largeIconUploadId: this.largeIconUploadId,
      };

      this.errorMessage = null;
      this.$root.loading = true;
      return this.$pwaManifestService.updateManifest(manifestUpdate)
        .then(() => this.$emit('saved'))
        .then(() => this.$root.$emit('alert-message', this.$t('generalSettings.savedSuccessfully'), 'success'))
        .then(() => window.setTimeout(() => { // Needed to give time to server to flush file saving
          this.init()
            .then(() => this.$nextTick().then(() => this.reset()));
        }, 200))
        .catch(e => {
          console.error(e);
          this.errorMessage = String(e);
        })
        .finally(() => this.$root.loading = false);
    },
  }
};
</script>
