<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

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
  <v-hover>
    <v-img
      slot-scope="{ hover }"
      :src="previewSrc" 
      role="presentation"
      transition="none"
      min-width="70"
      width="70"
      min-height="70"
      height="70"
      eager>
      <v-expand-transition>
        <div
          v-show="hover"
          class="justify-center light-grey-background absolute-full-size">
          <v-file-input
            v-if="!resetInput"
            id="imageFileInput"
            ref="file"
            :loading="sendingImage"
            prepend-icon="fas fa-camera z-index-two rounded-circle primary-border-color primary--text white py-1 absolute-all-center"
            accept="image/*"
            class="file-selector"
            rounded
            clearable
            @change="uploadFile" />
        </div>
      </v-expand-transition>
    </v-img>
  </v-hover>
</template>
<script>
export default {
  props: {
    value: {
      type: String,
      default: null,
    },
    defaultSrc: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    sendingImage: false,
    dataSrc: null,
    uploadInProgress: false,
    uploadProgress: 0,
    maxFileSize: 2097152,
    resetInput: false,
  }),
  computed: {
    isDefaultSrc() {
      return !this.dataSrc;
    },
    previewSrc() {
      if (this.dataSrc) {
        return this.$utils.convertImageDataAsSrc(this.dataSrc);
      } else {
        return this.defaultSrc;
      }
    },
  },
  watch: {
    sendingImage() {
      if (this.sendingImage) {
        document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
      } else {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      }
    },
    dataSrc() {
      this.$emit('data-changed', this.$utils.convertImageDataAsSrc(this.dataSrc));
    },
  },
  methods: {
    reset() {
      this.dataSrc = null;
      this.$emit('input', null);
      this.sendingImage = false;
      if (this.$refs.file) {
        this.resetInput = true;
        this.$nextTick().then(() => this.resetInput = false);
      }
    },
    uploadFile(file) {
      this.$root.$emit('close-alert-message');
      if (file && file.size) {
        if (file.type && file.type.indexOf('image/') !== 0) {
          this.$root.$emit('alert-message', this.$t('generalSettings.mustImage.label'), 'error');
          return;
        }
        if (file.size > this.maxFileSize) {
          this.$root.$emit('alert-message', this.$t('generalSettings.logo.tooBigFile.label'), 'error');
          return;
        }
        this.sendingImage = true;
        const self = this;
        return this.$uploadService.upload(file)
          .then(uploadId => {
            if (uploadId) {
              const reader = new FileReader();
              reader.onload = (e) => {
                self.dataSrc = e.target.result;
                self.$forceUpdate();
              };
              reader.readAsDataURL(file);
              this.$emit('input', uploadId);
            } else {
              this.$root.$emit('alert-message', this.$t('generalSettings.uploadingError'), 'error');
            }
          })
          .then(() => {
            if (this.value) {
              let interval;
              return new Promise((resolve, reject) => {
                interval = window.setInterval(() => {
                  this.$uploadService.getUploadProgress(this.value)
                    .then(percent => {
                      if (Number(percent) === 100) {
                        window.clearInterval(interval);
                        resolve();
                      }
                    })
                    .catch(e => {
                      window.clearInterval(interval);
                      reject(e);
                    });
                }, 200);
              });
            }
          })
          .catch(error => this.$root.$emit('alert-message', this.$t(String(error)), 'error'))
          .finally(() => this.sendingImage = false);
      }
    },
  },
};
</script>