<template>
  <v-app>
    <template v-if="displayed && enabled">
      <v-card
        class="application-body"
        flat>
        <v-list>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title class="text-title">
                {{ $t('UserSettings.pwa') }}
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ $t('UserSettings.pwa.subtitle') }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <v-list-item-action>
              <v-btn
                :aria-label="$t('UserSettings.pwa.install')"
                :loading="loading"
                :disabled="disabledButton"
                class="btn"
                @click.native="installPwa">
                {{ $t('UserSettings.pwa.install') }}
              </v-btn>
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
    enabled: true,
    disabledButton: false,
    loading: false,
  }),
  watch: {
    displayed() {
      this.$nextTick().then(() => this.$root.$emit('application-cache'));
    },
    enabled: {
      immediate: true,
      handler() {
        this.$root.$updateApplicationVisibility(this.enabled, this.$root.$el);
      },
    },
  },
  created() {
    document.addEventListener('showSettingsApps', () => this.displayed = true);
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    this.enabled = !!window.deferredPwaPrompt;
    if (this.enabled) {
      if (window.deferredPwaPromptTimeout) {
        window.clearTimeout(window.deferredPwaPromptTimeout);
      }
      window.addEventListener('appinstalled', this.hideApplication);
    }
  },
  mounted() {
    this.$nextTick().then(() => this.$root.$applicationLoaded());
  },
  beforeDestroy() {
    window.removeEventListener('appinstalled', this.hideApplication);
  },
  methods: {
    hideApplication() {
      this.enabled = false;
    },
    async installPwa() {
      this.loading = true;
      await window.deferredPwaPrompt.prompt();
      const { outcome } = await window.deferredPwaPrompt.userChoice;
      if (outcome === 'accepted') {
        window.deferredPwaPrompt = null;
        this.disabledButton = true;
      }
      this.loading = false;
    },
  },
};
</script>