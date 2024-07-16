self.addEventListener('install', event => self.skipWaiting());
self.addEventListener('activate', async () => {
  // This will be called only once when the service worker is activated.
  try {
    const urlB64ToUint8Array = (base64String) => {
      const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
      const base64 = (base64String + padding).replace(/\-/g, '+').replace(/_/g, '/')
      const rawData = atob(base64)
      const outputArray = new Uint8Array(rawData.length)
      for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i)
      }
      return outputArray
    }
    const applicationServerKey = urlB64ToUint8Array(
      'BJ5IxJBWdeqFDJTvrZ4wNRu7UY2XigDXjgiUBYEYVXDudxhEs0ReOJRBcBHsPYgZ5dyV8VjyqzbQKS8V7bUAglk'
    )
    const options = { applicationServerKey, userVisibleOnly: true }
    const subscription = await self.registration.pushManager.subscribe(options)
    console.log(JSON.stringify(subscription))
  } catch (err) {
    console.log('Error', err)
  }
})

self.addEventListener('push', (event) => {
  if (self?.Notification?.permission === 'granted') {
    const data = event?.data?.json?.() || {};
    const title = data.title || 'Something Has Happened';
    const body = data.message || 'Summary.';
    const icon = data.icon || self.location.origin + '/portal/rest/v1/platform/branding/manifest/smallIcon?size=50x50';
    const image = data.icon || self.location.origin + '/portal/rest/v1/platform/branding/manifest/largeIcon?size=72x72';
    const path = data.path || '/';
    self.registration.showNotification(title, {
      body,
      icon,
      badge: image,
      image,
      data: {
        path,
      },
    });
    self.addEventListener('notificationclick', (event) => {
      event.notification.close(); 
      console.warn('event.notification', event, event.notification);
      clients.openWindow(self.location.origin + event.notification.data.path);
    });
  }
});
