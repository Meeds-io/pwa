<template>
  <v-app>
    <template v-if="displayed">
      <v-card
        class="application-body"
        flat>
        <v-list>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title class="text-title">
                <template v-if="isMobile">
                  {{ $t('UserSettings.pwa.mobile') }}
                </template>
                <template v-else>
                  {{ $t('UserSettings.pwa.desktop') }}
                </template>
              </v-list-item-title>
              <v-list-item-subtitle v-if="!isMobile">
                {{ $t('UserSettings.pwa.subtitle') }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <v-list-item-action>
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
        </v-list>
      </v-card>
    </template>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    displayed: true,
    installed: true,
    pwaEnabled: eXo.env.portal.pwaEnabled,
    pwaSupported: true,
    disabledButton: false,
    loading: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify?.breakpoint?.mdAndDown;
    },
  },
  watch: {
    displayed() {
      this.$nextTick().then(() => this.$root.$emit('application-cache'));
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
      await window.deferredPwaPrompt.prompt();
      const { outcome } = await window.deferredPwaPrompt.userChoice;
      if (outcome === 'accepted') {
        window.deferredPwaPrompt = null;
        this.installed = true;
      }
      this.loading = false;
    },
  },
};
</script>