<template>
  <v-app>
    <v-card
      class="application-body"
      flat>
      <v-card-title class="text-title pb-0">
        {{ $t('OfflineApp.pwa.appIsOffline') }}
      </v-card-title>
      <extension-registry-components
        name="OfflineApp"
        type="offline-content"
        class=" d-flex flex-column" />
    </v-card>
  </v-app>
</template>
<script>
export default {
  mounted() {
    this.$root.$applicationLoaded();
  },
};
</script>