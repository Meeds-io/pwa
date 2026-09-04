/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.pwa.service;

import static io.meeds.pwa.service.PwaNotificationService.EVENT_ACTION_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_NOTIFICATION_ID_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_USERNAME_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_MARK_READ_USER_ACTION;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_OPEN_UI_ACTION;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_DELAY_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_RECEIVED_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_SENT_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_RECEIVED;
import static io.meeds.pwa.service.PwaNotificationService.WEB_NOTIFICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.Constants;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.services.organization.UserProfileHandler;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.services.resources.Orientation;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.resources.impl.LocaleConfigImpl;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.plugin.DefaultPwaNotificationPlugin;
import io.meeds.pwa.storage.PwaNotificationStorage;

import lombok.SneakyThrows;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@SpringBootTest(classes = {
  PwaNotificationService.class,
})
@TestPropertySource(properties = {
  "pwa.notifications.push.token.ttl.excessiveDelayThreshold=30",
})
public class PwaNotificationServiceTest {

  private static final String          DELAY_MS_KEY          = "delayMs";

  private static final String          SUBSCRIPTION_ID_KEY   = "subscriptionId";

  private static final String          SUBSCRIPTION_ID       = "58269855";

  private static final String          SUBSCRIPTION_ENDPOINT = "http://localhost/endpoint";

  private static final String          PUSH_AUTH_SCHEME      = "PWA-Notification";

  private static final String          PUSH_DEVICE_SECRET    = Base64.getEncoder()
                                                                     .encodeToString("testPushDeviceSecret".getBytes(StandardCharsets.UTF_8));

  private static final String          PUSH_ACCESS_TOKEN     = "generatedPushAccessTokenWithEnoughLength";

  private static final PluginKey       PLUGIN_KEY            = PluginKey.key("TestPlugin");

  private static final long            NOTIFICATION_ID       = 12l;

  private static final String          TEST_USER             = "testUser";

  @MockitoBean
  private PwaManifestService           pwaManifestService;

  @MockitoBean
  private PwaSubscriptionService       pwaSubscriptionService;

  @MockitoBean
  private PwaNotificationStorage       pwaNotificationStorage;

  @MockitoBean
  private WebNotificationService       webNotificationService;

  @MockitoBean
  private ListenerService              listenerService;

  @MockitoBean
  private OrganizationService          organizationService;

  @MockitoBean
  private LocaleConfigService          localeConfigService;

  @MockitoBean
  private ResourceBundleService        resourceBundleService;

  @MockitoBean
  private DefaultPwaNotificationPlugin defaultPwaNotificationPlugin;

  @MockitoBean
  private PushService                  pushService;

  @MockitoBean
  private PwaNotificationTokenService  pwaNotificationTokenService;

  @Autowired
  private PwaNotificationService       pwaNotificationService;

  @MockitoBean
  private NotificationInfo             notification;

  @MockitoBean
  private UserProfile                  userProfile;

  @MockitoBean
  private UserProfileHandler           userProfileHandler;

  @MockitoBean
  private PwaNotificationMessage       notificationMessage;

  @MockitoBean
  private UserPushSubscription         userPushSubscription;

  @MockitoBean
  private HttpResponse                 httpResponse;

  @MockitoBean
  private StatusLine                   statusLine;

  @Test
  public void getNotification() throws IllegalAccessException, ObjectNotFoundException {
    assertThrows(ObjectNotFoundException.class, () -> pwaNotificationService.getNotification(NOTIFICATION_ID, TEST_USER));
    mockWebNotificationNoAccess();
    assertThrows(IllegalAccessException.class, () -> pwaNotificationService.getNotification(NOTIFICATION_ID, TEST_USER));
    mockWebNotification();
    mockUserLanguage();

    when(defaultPwaNotificationPlugin.process(eq(notification), any())).thenReturn(notificationMessage);
    PwaNotificationMessage result = pwaNotificationService.getNotification(NOTIFICATION_ID, TEST_USER);
    assertEquals(notificationMessage, result);
    verify(notificationMessage).setActions(argThat(list -> list.size()
        == 1 && list.get(0).getAction().equals(PWA_NOTIFICATION_MARK_READ_USER_ACTION)));
    verify(notificationMessage).setRequireInteraction(true);
    verify(notificationMessage).setRenotify(true);
    verify(notificationMessage).setSilent(false);
    verify(notificationMessage).setLang("fr");
    verify(notificationMessage).setDir("ltr");
    verify(notificationMessage).setTag(String.valueOf(NOTIFICATION_ID));
    verify(notificationMessage).setUrl("/");
  }

  @Test
  public void getNotificationFromPush() throws Exception { // NOSONAR
    String[] payloadParts = createPushNotificationAndGetPayloadParts(true);
    String token = payloadParts[3];
    when(pwaNotificationTokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    String authorizationHeader = buildAuthorizationHeader(token, SUBSCRIPTION_ID, System.currentTimeMillis());
    mockUserLanguage();
    when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(userPushSubscription);
    when(defaultPwaNotificationPlugin.process(eq(notification), any())).thenReturn(notificationMessage);

    PwaNotificationMessage result = pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                                                   authorizationHeader,
                                                                                   null);

    assertEquals(notificationMessage, result);
    verify(defaultPwaNotificationPlugin).process(eq(notification), any());
  }

  @Test
  public void getNotificationFromPushWithAuthenticatedUser() throws Exception { // NOSONAR
    mockWebNotification();
    mockUserLanguage();
    when(defaultPwaNotificationPlugin.process(eq(notification), any())).thenReturn(notificationMessage);

    PwaNotificationMessage result = pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                                                   null,
                                                                                   TEST_USER);

    assertEquals(notificationMessage, result);
    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(NOTIFICATION_ID), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(NOTIFICATION_ID), any());
  }

  @Test
  public void getNotificationFromPushWhenInvalidAuthorization() throws Exception { // NOSONAR
    assertThrows(IllegalAccessException.class, () -> pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID, null, null));
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                                      PUSH_AUTH_SCHEME + " token=\"unknown\"",
                                                                      null));

    String[] payloadParts = createPushNotificationAndGetPayloadParts(true);
    String token = payloadParts[3];
    long timestamp = System.currentTimeMillis();
    when(pwaNotificationTokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(userPushSubscription);

    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                                      buildAuthorizationHeader(token,
                                                                                               SUBSCRIPTION_ID,
                                                                                               timestamp,
                                                                                               "invalidProof"),
                                                                      null));
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID + 1,
                                                                      buildAuthorizationHeader(token,
                                                                                               SUBSCRIPTION_ID,
                                                                                               timestamp),
                                                                      null));
  }

  @Test
  public void updateNotificationFromPush() throws Exception { // NOSONAR
    String[] payloadParts = createPushNotificationAndGetPayloadParts(true);
    String token = payloadParts[3];
    when(pwaNotificationTokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    String authorizationHeader = buildAuthorizationHeader(token, SUBSCRIPTION_ID, System.currentTimeMillis());
    when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(userPushSubscription);

    pwaNotificationService.updateNotificationFromPush(NOTIFICATION_ID,
                                                      PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                      authorizationHeader,
                                                      null);

    verify(webNotificationService).markRead(String.valueOf(NOTIFICATION_ID));
  }

  @Test
  public void updateNotificationFromPushWithAuthenticatedUser() throws Exception { // NOSONAR
    mockWebNotification();

    pwaNotificationService.updateNotificationFromPush(NOTIFICATION_ID,
                                                      PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                      null,
                                                      TEST_USER);

    verify(webNotificationService).markRead(String.valueOf(NOTIFICATION_ID));
    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(NOTIFICATION_ID), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(NOTIFICATION_ID), any());
  }

  @Test
  public void reportPushDeliveryDelay() throws Exception { // NOSONAR
    mockWebNotification();
    mockSubscription(true);
    when(pwaNotificationTokenService.validateToken(PUSH_ACCESS_TOKEN, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(notification.getOwnerParameter()).thenReturn(null);
    long sentAt = System.currentTimeMillis() - 60000;
    long receivedAt = sentAt + 60000;
    String authorizationHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, System.currentTimeMillis());

    pwaNotificationService.reportPushDeliveryDelay(NOTIFICATION_ID,
                                                   authorizationHeader,
                                                   null,
                                                   sentAt,
                                                   receivedAt);

    verify(pwaNotificationTokenService).validateToken(PUSH_ACCESS_TOKEN, NOTIFICATION_ID, SUBSCRIPTION_ID);
    verify(pwaNotificationTokenService, never()).consumeToken(PUSH_ACCESS_TOKEN, NOTIFICATION_ID, SUBSCRIPTION_ID);
    verify(pwaNotificationStorage).recordExcessivePushDeliveryDelay(eq(TEST_USER),
                                                                    eq(SUBSCRIPTION_ID),
                                                                    longThat(delay -> delay >= 30000l));
    verify(notification).setOwnerParameter(argThat(params -> params.containsKey(PWA_NOTIFICATION_PUSH_SENT_TIME)
                                                             && params.containsKey(PWA_NOTIFICATION_PUSH_RECEIVED_TIME)
                                                             && params.containsKey(PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME)
                                                             && params.containsKey(PWA_NOTIFICATION_PUSH_DELAY_TIME)));
    verify(webNotificationService).updateNotificationParameters(eq(String.valueOf(NOTIFICATION_ID)),
                                                                argThat(params -> params.containsKey(PWA_NOTIFICATION_PUSH_SENT_TIME)
                                                                                  && params.containsKey(PWA_NOTIFICATION_PUSH_RECEIVED_TIME)
                                                                                  && params.containsKey(PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME)
                                                                                  && params.containsKey(PWA_NOTIFICATION_PUSH_DELAY_TIME)));
    verify(listenerService).broadcast(PWA_NOTIFICATION_RECEIVED, userPushSubscription, notification);
  }

  @Test
  public void reportPushDeliveryDelayWithAuthenticatedUser() throws Exception { // NOSONAR
    mockWebNotification();
    mockSubscription(true);
    when(notification.getOwnerParameter()).thenReturn(new HashMap<>());
    long sentAt = System.currentTimeMillis() - 60000;
    long receivedAt = sentAt + 60000;
    String authorizationHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, System.currentTimeMillis());

    pwaNotificationService.reportPushDeliveryDelay(NOTIFICATION_ID,
                                                   authorizationHeader,
                                                   TEST_USER,
                                                   sentAt,
                                                   receivedAt);

    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(NOTIFICATION_ID), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(NOTIFICATION_ID), any());
    verify(pwaNotificationStorage).recordExcessivePushDeliveryDelay(eq(TEST_USER), eq(SUBSCRIPTION_ID), anyLong());
    verify(webNotificationService).updateNotificationParameters(eq(String.valueOf(NOTIFICATION_ID)), any());
    verify(listenerService).broadcast(PWA_NOTIFICATION_RECEIVED, userPushSubscription, notification);
  }

  @Test
  public void getAndResetPushDeliveryDelayStatus() {
    Map<String, Object> status = Map.of(DELAY_MS_KEY, 60000L, SUBSCRIPTION_ID_KEY, SUBSCRIPTION_ID);
    when(pwaNotificationStorage.getPushDeliveryDelayStatus(TEST_USER, SUBSCRIPTION_ID, 3600000L)).thenReturn(status);

    assertEquals(status, pwaNotificationService.getPushDeliveryDelayStatus(TEST_USER, SUBSCRIPTION_ID));

    pwaNotificationService.resetPushDeliveryDelay(TEST_USER, SUBSCRIPTION_ID);
    verify(pwaNotificationStorage).resetPushDeliveryDelay(TEST_USER, SUBSCRIPTION_ID);
  }

  @Test
  public void updateNotification() throws IllegalAccessException, ObjectNotFoundException {
    assertThrows(ObjectNotFoundException.class,
                 () -> pwaNotificationService.updateNotification(NOTIFICATION_ID,
                                                                 PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                                 TEST_USER));
    mockWebNotificationNoAccess();
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.updateNotification(NOTIFICATION_ID,
                                                                 PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                                 "testUser2"));
    mockWebNotification();
    pwaNotificationService.updateNotification(NOTIFICATION_ID, PWA_NOTIFICATION_MARK_READ_USER_ACTION, TEST_USER);
    verify(webNotificationService).markRead(String.valueOf(NOTIFICATION_ID));
    verifyNoInteractions(defaultPwaNotificationPlugin);

    String action = "otherAction";
    pwaNotificationService.updateNotification(NOTIFICATION_ID, action, TEST_USER);
    verify(defaultPwaNotificationPlugin).handleAction(notification, action, TEST_USER);
  }

  @Test
  public void create() throws Exception { // NOSONAR
    ScheduledFuture<?> future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNull(future);
    when(pwaManifestService.isPwaEnabled()).thenReturn(true);
    future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verifyNoInteractions(listenerService);

    mockWebNotification();
    mockSubscription(false);
    when(pushService.send(any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(401);

    future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService, never()).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
    verify(pushService).send(argThat(n -> new String(n.getPayload(),
                                                     StandardCharsets.UTF_8).startsWith("%s:%s:%s".formatted(WEB_NOTIFICATION,
                                                                                                             NOTIFICATION_ID,
                                                                                                             PWA_NOTIFICATION_OPEN_UI_ACTION))));

    when(statusLine.getStatusCode()).thenReturn(410);
    future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);

    when(statusLine.getStatusCode()).thenReturn(200);
    future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(1, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
  }

  @Test
  public void createWhenSubscriptionHasDeviceSecretAddsPushAccessToken() throws Exception { // NOSONAR
    String[] payloadParts = createPushNotificationAndGetPayloadParts(true);

    assertEquals(6, payloadParts.length);
    assertEquals(WEB_NOTIFICATION, payloadParts[0]);
    assertEquals(String.valueOf(NOTIFICATION_ID), payloadParts[1]);
    assertEquals(PWA_NOTIFICATION_OPEN_UI_ACTION, payloadParts[2]);
    assertEquals(PUSH_ACCESS_TOKEN, payloadParts[3]);
    assertEquals(SUBSCRIPTION_ID, payloadParts[4]);
    assertFalse(payloadParts[5].isBlank());
  }

  @Test
  public void createWhenSubscriptionHasNoDeviceSecretKeepsLegacyPayload() throws Exception { // NOSONAR
    String[] payloadParts = createPushNotificationAndGetPayloadParts(false);

    assertEquals(6, payloadParts.length);
    assertEquals(WEB_NOTIFICATION, payloadParts[0]);
    assertEquals(String.valueOf(NOTIFICATION_ID), payloadParts[1]);
    assertEquals(PWA_NOTIFICATION_OPEN_UI_ACTION, payloadParts[2]);
    assertEquals("", payloadParts[3]);
    assertEquals("", payloadParts[4]);
    assertFalse(payloadParts[5].isBlank());
  }

  @Test
  public void createWithHashmap() throws Exception { // NOSONAR
    ScheduledFuture<?> future = pwaNotificationService.create(new HashMap<>());
    assertNull(future);
    when(pwaManifestService.isPwaEnabled()).thenReturn(true);

    Map<String, Object> params = new HashMap<>();
    params.put(EVENT_NOTIFICATION_ID_PARAM_NAME, NOTIFICATION_ID);
    params.put(EVENT_ACTION_PARAM_NAME, "open");
    params.put(EVENT_USERNAME_PARAM_NAME, "john");
    future = pwaNotificationService.create(params);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verifyNoInteractions(listenerService);
  }

  private String[] createPushNotificationAndGetPayloadParts(boolean withDeviceSecret) throws Exception { // NOSONAR
    when(pwaManifestService.isPwaEnabled()).thenReturn(true);
    mockWebNotification();
    mockSubscription(withDeviceSecret);
    if (withDeviceSecret) {
      when(pwaNotificationTokenService.createToken(TEST_USER, NOTIFICATION_ID, SUBSCRIPTION_ID)).thenReturn(PUSH_ACCESS_TOKEN);
    }
    when(pushService.send(any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);

    ScheduledFuture<?> future = pwaNotificationService.create(NOTIFICATION_ID);

    assertNotNull(future);
    assertEquals(1, (int) future.get());
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(pushService).send(notificationCaptor.capture());
    return new String(notificationCaptor.getValue().getPayload(), StandardCharsets.UTF_8).split(":", -1);
  }

  private void mockSubscription(boolean withDeviceSecret) throws Exception { // NOSONAR
    when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    lenient().when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(userPushSubscription);
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(userPushSubscription.getPushDeviceSecret()).thenReturn(withDeviceSecret ? PUSH_DEVICE_SECRET : null);
  }

  private String buildAuthorizationHeader(String token, String subscriptionId, long timestamp) throws Exception { // NOSONAR
    return buildAuthorizationHeader(token, subscriptionId, timestamp, computeHmac(token, subscriptionId, timestamp));
  }

  private String buildAuthorizationHeader(String token, String subscriptionId, long timestamp, String proof) {
    return PUSH_AUTH_SCHEME + " token=\"" + token + "\"," + "subscriptionId=\"" + subscriptionId + "\"," + "timestamp=\"" +
        timestamp + "\"," + "proof=\"" + proof + "\"";
  }

  private String computeHmac(String token, String subscriptionId, long timestamp) throws Exception { // NOSONAR
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(PUSH_DEVICE_SECRET), "HmacSHA256"));
    String value = NOTIFICATION_ID + ":" + token + ":" + subscriptionId + ":" + timestamp;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }

  @SneakyThrows
  private void mockUserLanguage() {
    when(organizationService.getUserProfileHandler()).thenReturn(userProfileHandler);
    when(userProfileHandler.findUserProfileByName(TEST_USER)).thenReturn(userProfile);
    when(userProfile.getAttribute(Constants.USER_LANGUAGE)).thenReturn("fr");
    LocaleConfigImpl localeConfig = new LocaleConfigImpl();
    localeConfig.setLocale(Locale.FRENCH);
    localeConfig.setOrientation(Orientation.LT);
    when(localeConfigService.getLocaleConfig("fr")).thenReturn(localeConfig);
  }

  private void mockWebNotification() {
    when(webNotificationService.getNotificationInfo(String.valueOf(NOTIFICATION_ID))).thenReturn(notification);
    when(notification.getTo()).thenReturn(TEST_USER);
    when(notification.getId()).thenReturn(String.valueOf(NOTIFICATION_ID));
    when(notification.getKey()).thenReturn(PLUGIN_KEY);
  }

  private void mockWebNotificationNoAccess() {
    when(webNotificationService.getNotificationInfo(String.valueOf(NOTIFICATION_ID))).thenReturn(notification);
    when(notification.getKey()).thenReturn(PLUGIN_KEY);
  }
}
