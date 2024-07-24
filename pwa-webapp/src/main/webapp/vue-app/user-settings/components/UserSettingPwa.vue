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
              :disabled="pwaSupported && pwaEnabled"
              bottom>
              <template #activator="{on, attrs}">
                <div
                  v-on="on"
                  v-bind="attrs">
                  <v-btn
                    :aria-label="$t('UserSettings.pwa.install')"
                    :loading="loading"
                    :disabled="disabledButton || !pwaSupported || !pwaEnabled"
                    class="btn"
                    @click.native="installPwa">
                    {{ $t('UserSettings.pwa.install') }}
                  </v-btn>
                </div>
              </template>
              <span v-if="!pwaSupported">
                {{ $t('UserSettings.pwa.browserNotSupported') }}
              </span>
              <span v-else-if="!pwaEnabled">
                {{ $t('UserSettings.pwa.pwaNotEnabled') }}
              </span>
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
                class="border-color py-2 px-3"
                flat
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
      </v-list>
    </v-card>
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
    loading: false,
    permissionLoading: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify?.breakpoint?.mdAndDown;
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
    this.pwaSupported = 'onbeforeinstallprompt' in window;
    const pwaMode = !!window?.matchMedia('(display-mode: standalone)')?.matches;
    this.installed = pwaMode || (this.pwaEnabled && this.pwaSupported && !window.deferredPwaPrompt) || false;
    if (window.deferredPwaPromptTimeout) {
      window.clearTimeout(window.deferredPwaPromptTimeout);
    }
  },
  mounted() {
    this.$root.$applicationLoaded();
  },
  methods: {
    async installPwa() {
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
    },
    async requestPermission() {
      this.permissionLoading = true;
      try {
        await Notification.requestPermission();
      } finally {
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