<template>
  <v-app>
    <v-card
      class="application-body"
      flat>
      <v-card-title class="text-title pb-0">
        <template v-if="isMobile">
          {{ $t('UserSettings.pwa.mobile') }}
        </template>
        <template v-else>
          {{ $t('UserSettings.pwa.desktop') }}
        </template>
      </v-card-title>
      <v-list>
        <v-list-item dense>
          <v-list-item-content>
            <v-list-item-title class="text-wrap">
              {{ $t('UserSettings.pwa.install.description') }}
            </v-list-item-title>
          </v-list-item-content>
          <v-list-item-action class="mt-0 mb-auto">
            <v-card
              v-if="installed"
              class="border-color py-2 px-3"
              disabled
              flat>
              <v-icon class="success--text me-2" size="18">fa-check</v-icon>
              {{ $t('UserSettings.pwa.installed') }}
            </v-card>
            <v-tooltip
              v-else
              :disabled="pwaEnabled"
              bottom>
              <template #activator="{on, attrs}">
                <div
                  v-on="on"
                  v-bind="attrs">
                  <v-btn
                    :aria-label="$t('UserSettings.pwa.install')"
                    :loading="loading"
                    :disabled="disabledButton || !pwaEnabled"
                    class="btn"
                    @click.native="installPwa">
                    {{ $t('UserSettings.pwa.install') }}
                  </v-btn>
                </div>
              </template>
              <span>{{ $t('UserSettings.pwa.pwaNotEnabled') }}</span>
            </v-tooltip>
          </v-list-item-action>
        </v-list-item>
        <v-list-item dense class="mt-3">
          <v-list-item-content>
            <v-list-item-title class="text-wrap">
              {{ $t('UserSettings.pwa.notification.description') }}
            </v-list-item-title>
          </v-list-item-content>
          <v-list-item-action class="mt-0 mb-auto">
            <template v-if="installed">
              <v-card
                v-if="notificationPermission === 'granted'"
                class="border-color py-2 px-3"
                disabled
                flat>
                <v-icon class="success--text me-2" size="18">fa-check</v-icon>
                {{ $t('UserSettings.pwa.notification.granted') }}
              </v-card>
              <v-card
                v-else-if="notificationPermission === 'denied'"
                class="border-color py-2 px-3"
                disabled
                flat>
                <v-icon class="error--text me-2" size="18">fa-times</v-icon>
                {{ $t('UserSettings.pwa.notification.denied') }}
              </v-card>
              <v-btn
                v-else
                :aria-label="$t('UserSettings.pwa.notification.choose')"
                :loading="permissionLoading"
                class="btn"
                text
                @click="requestPermission">
                {{ $t('UserSettings.pwa.notification.choose') }}
              </v-btn>
            </template>
            <v-tooltip
              v-else
              bottom>
              <template #activator="{on, attrs}">
                <div
                  v-on="on"
                  v-bind="attrs">
                  <v-btn
                    :aria-label="$t('UserSettings.pwa.notification.choose')"
                    disabled
                    class="btn">
                    {{ $t('UserSettings.pwa.notification.choose') }}
                  </v-btn>
                </div>
              </template>
              <span v-if="!pwaSupported">
                {{ $t('UserSettings.pwa.browserNotSupported') }}
              </span>
              <span v-else-if="!pwaEnabled">
                {{ $t('UserSettings.pwa.pwaNotEnabled') }}
              </span>
              <span v-else>
                {{ $t('UserSettings.pwa.notification.pwaNotInstalled') }}
              </span>
            </v-tooltip>
          </v-list-item-action>
        </v-list-item>
        <extension-registry-components
          :params="extensionParams"
          name="PwaUserSettings"
          type="pwa-user-settings"
          class=" d-flex flex-column" />
      </v-list>
    </v-card>
    <user-setting-pwa-ios-help-drawer
      v-if="isIOs"
      ref="pwaSupportHelpDrawer" />
    <user-setting-pwa-help-drawer
      v-else
      ref="pwaSupportHelpDrawer" />
  </v-app>
</template>
<script>
export default {
  data: () => ({
    displayed: true,
    installed: true,
    pwaEnabled: eXo.env.portal.pwaEnabled,
    pwaSupported: true,
    notificationPermission: Notification.permission,
    disabledButton: false,
    isIOs: false,
    loading: false,
    permissionLoading: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify?.breakpoint?.mdAndDown;
    },
    extensionParams() {
      return {
        isMobile: this.isMobile,
        pwaEnabled: this.pwaEnabled,
        pwaSupported: this.pwaSupported,
        loading: this.loading,
        installed: this.installed,
      };
    },
  },
  watch: {
    displayed() {
      if (this.displayed) {
        this.$nextTick().then(() => this.$root.$emit('application-cache'));
      }
      this.$root.$updateApplicationVisibility(this.displayed);
    },
  },
  created() {
    document.addEventListener('showSettingsApps', () => this.displayed = true);
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('pwa-beforeinstallprompt', this.checkInstalled);
    this.pwaSupported = 'onbeforeinstallprompt' in window;
    this.isIOs = this.isIOsAgent();
    this.checkInstalled();
  },
  mounted() {
    this.$root.$applicationLoaded();
  },
  methods: {
    async checkInstalled() {
      const pwaMode = !!(window?.matchMedia('(display-mode: standalone)')?.matches || window?.matchMedia('(display-mode: tabbed)')?.matches);
      const installed = pwaMode || (this.pwaEnabled && this.pwaSupported && !window.deferredPwaPrompt) || false;
      const registration = await navigator?.serviceWorker?.getRegistration?.();
      this.installed = installed && !!registration;
      if (window.deferredPwaPromptTimeout) {
        window.clearTimeout(window.deferredPwaPromptTimeout);
      }
    },
    async installPwa() {
      if (this.pwaSupported) {
        this.loading = true;
        try {
          await window.deferredPwaPrompt.prompt();
          const { outcome } = await window.deferredPwaPrompt.userChoice;
          if (outcome === 'accepted') {
            window.deferredPwaPrompt = null;
            this.installed = true;
            pwa.init();
          }
        } finally {
          this.loading = false;
        }
      } else {
        this.$refs.pwaSupportHelpDrawer.open();
      }
    },
    isIOsAgent() {
      const traditionalTouch = /iPhone|iPad|iPod/.test(navigator.userAgent);
      const modernIPad = navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
      return traditionalTouch || modernIPad;
    },
    async requestPermission() {
      this.permissionLoading = true;
      try {
        await Notification.requestPermission();
      } finally {
        document.dispatchEvent(new CustomEvent('close-alert-message'));
        this.notificationPermission = Notification.permission;
        if (this.notificationPermission === 'granted') {
          pwa.init();
        }
        this.permissionLoading = false;
      }
    },
  },
};
</script>