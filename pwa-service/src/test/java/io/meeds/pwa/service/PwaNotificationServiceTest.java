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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.test.util.ReflectionTestUtils;

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

import io.meeds.pwa.model.DeviceNotificationSetting;
import io.meeds.pwa.model.PwaDirectNotificationBuilder;
import io.meeds.pwa.model.PwaNotificationAction;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.plugin.DefaultPwaNotificationPlugin;
import io.meeds.pwa.plugin.PwaDirectNotificationActionPlugin;
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
/*
 * The push wire itself is never exercised here: PushService is mocked, so VAPID
 * encryption, the delivery to a browser push service and the service worker's
 * showNotification contract are covered only by a manual run on the acceptance
 * server. These tests pin the payload and the decisions around it, not delivery.
 */
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

  @MockitoBean
  private PwaDirectNotificationActionPlugin directNotificationActionPlugin;

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
    when(pwaNotificationTokenService.validateToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
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
    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
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
    when(pwaNotificationTokenService.validateToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
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
    when(pwaNotificationTokenService.validateToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(token, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
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
    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
  }

  @Test
  public void reportPushDeliveryDelay() throws Exception { // NOSONAR
    mockWebNotification();
    mockSubscription(true);
    when(pwaNotificationTokenService.validateToken(PUSH_ACCESS_TOKEN, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(notification.getOwnerParameter()).thenReturn(null);
    long sentAt = System.currentTimeMillis() - 60000;
    long receivedAt = sentAt + 60000;
    String authorizationHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, System.currentTimeMillis());

    pwaNotificationService.reportPushDeliveryDelay(NOTIFICATION_ID,
                                                   authorizationHeader,
                                                   null,
                                                   sentAt,
                                                   receivedAt);

    verify(pwaNotificationTokenService).validateToken(PUSH_ACCESS_TOKEN, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID);
    verify(pwaNotificationTokenService, never()).consumeToken(PUSH_ACCESS_TOKEN, String.valueOf(NOTIFICATION_ID), SUBSCRIPTION_ID);
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

    verify(pwaNotificationTokenService, never()).validateToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
    verify(pwaNotificationTokenService, never()).consumeToken(any(), eq(String.valueOf(NOTIFICATION_ID)), any());
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

  @Test
  public void scheduleDirectNotificationFiresSelfContainedPush() throws Exception { // NOSONAR
    List<String> builtForSubscriptions = new ArrayList<>();
    PwaDirectNotificationBuilder builder = subscriptionId -> {
      builtForSubscriptions.add(subscriptionId);
      PwaNotificationMessage message = new PwaNotificationMessage();
      message.setTitle("John in General");
      message.setBody("Hello there");
      message.setTag("!room:server");
      message.setRenotify(true);
      message.setData(Map.of("roomId", "!room:server"));
      return message;
    };
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      // PWA disabled: nothing scheduled
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 300l, builder);
      verifyNoInteractions(executorService);

      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(false);
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 300l, builder);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);
      verify(executorService).schedule(fire.capture(), eq(300l), eq(TimeUnit.SECONDS));

      when(pushService.send(any())).thenReturn(httpResponse);
      when(httpResponse.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      fire.getValue().run();
      ArgumentCaptor<Notification> pushCaptor = ArgumentCaptor.forClass(Notification.class);
      verify(pushService).send(pushCaptor.capture());
      String payload = new String(pushCaptor.getValue().getPayload(), StandardCharsets.UTF_8);
      assertTrue(payload.startsWith("DIRECT_NOTIFICATION:"));
      String json = payload.substring(payload.indexOf(':') + 1);
      assertTrue(json.contains("\"title\":\"John in General\""));
      assertTrue(json.contains("\"body\":\"Hello there\""));
      assertTrue(json.contains("\"tag\":\"!room:server\""));
      assertTrue(json.contains("\"roomId\":\"!room:server\""));
      // the builder is invoked once per device, with that device's id
      assertEquals(List.of(SUBSCRIPTION_ID), builtForSubscriptions);
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void fireDirectNotificationGuards() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(false);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);

      // a null build cancels the send at fire time (fire-time guard)
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, subscriptionId -> null);
      verify(executorService, times(1)).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      fire.getValue().run();
      verifyNoInteractions(pushService);

      // the device unsubscribed during the delay window: nothing sent
      PwaNotificationMessage message = new PwaNotificationMessage();
      message.setTitle("A title");
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, subscriptionId -> message);
      verify(executorService, times(2)).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(null);
      fire.getValue().run();
      verifyNoInteractions(pushService);

      // a gone subscription (HTTP 410) is deleted
      when(pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID)).thenReturn(userPushSubscription);
      when(pushService.send(any())).thenReturn(httpResponse);
      when(httpResponse.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(410);
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, subscriptionId -> message);
      verify(executorService, times(3)).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      fire.getValue().run();
      verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void directNotificationPayloadCapsBody() throws Exception { // NOSONAR
    PwaNotificationMessage message = new PwaNotificationMessage();
    message.setTitle("A title");
    message.setBody("x".repeat(10000));
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(false);
      when(pushService.send(any())).thenReturn(httpResponse);
      when(httpResponse.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);

      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 300l, subscriptionId -> message);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);
      verify(executorService).schedule(fire.capture(), eq(300l), eq(TimeUnit.SECONDS));
      fire.getValue().run();

      ArgumentCaptor<Notification> pushCaptor = ArgumentCaptor.forClass(Notification.class);
      verify(pushService).send(pushCaptor.capture());
      String payload = new String(pushCaptor.getValue().getPayload(), StandardCharsets.UTF_8);
      // Web Push payloads are capped around 4KB once encrypted: the body is
      // shrunk so the whole payload stays under the margin
      assertTrue(payload.getBytes(StandardCharsets.UTF_8).length <= "DIRECT_NOTIFICATION:".length() + 3800);
      assertTrue(payload.contains("..."));
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void scheduleDirectNotificationFiresOncePerDevice() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      UserPushSubscription secondSubscription = mock(UserPushSubscription.class);
      when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
      when(secondSubscription.getId()).thenReturn("secondSubscriptionId");
      when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(List.of(userPushSubscription, secondSubscription));

      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 300l, subscriptionId -> null);
      // one deferred fire per subscribed device
      verify(executorService, times(2)).schedule(any(Runnable.class), eq(300l), eq(TimeUnit.SECONDS));
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void fireDirectNotificationReportsSendFailure() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(false);
      PwaNotificationMessage message = new PwaNotificationMessage();
      message.setTitle("A title");
      AtomicReference<String> failedSubscription = new AtomicReference<>();
      PwaDirectNotificationBuilder builder = new PwaDirectNotificationBuilder() {
        @Override
        public PwaNotificationMessage build(String subscriptionId) {
          return message;
        }

        @Override
        public void onSendFailure(String subscriptionId, PwaNotificationMessage failedMessage) {
          failedSubscription.set(subscriptionId);
        }
      };

      when(pushService.send(any())).thenReturn(httpResponse);
      when(httpResponse.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, builder);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);
      verify(executorService).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      fire.getValue().run();
      // a non-2xx send reports back so the caller can re-arm its guard
      assertEquals(SUBSCRIPTION_ID, failedSubscription.get());

      // 410 means the device is gone: deleted, not reported as a failure
      failedSubscription.set(null);
      when(statusLine.getStatusCode()).thenReturn(410);
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, builder);
      verify(executorService, times(2)).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      fire.getValue().run();
      verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
      assertNull(failedSubscription.get());
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void scheduleDirectNotificationHonorsPerDeviceSettings() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      UserPushSubscription disabledDevice = new UserPushSubscription();
      disabledDevice.setId("disabledDevice");
      disabledDevice.setNotificationSetting("chat", new DeviceNotificationSetting(false, null));
      UserPushSubscription slowDevice = new UserPushSubscription();
      slowDevice.setId("slowDevice");
      slowDevice.setNotificationSetting("chat", new DeviceNotificationSetting(true, 10));
      UserPushSubscription defaultDevice = new UserPushSubscription();
      defaultDevice.setId("defaultDevice");
      when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(List.of(disabledDevice, slowDevice, defaultDevice));

      // the kind is enabled on at least one device
      assertTrue(pwaNotificationService.canReceiveDirectNotifications(TEST_USER, "chat"));

      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 300l, subscriptionId -> null);
      // the disabled device is never scheduled; the device with its own delay
      // fires after it, the device without a setting follows the default
      verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS));
      verify(executorService, times(1)).schedule(any(Runnable.class), eq(600l), eq(TimeUnit.SECONDS));
      verify(executorService, times(1)).schedule(any(Runnable.class), eq(300l), eq(TimeUnit.SECONDS));

      // the kind disabled on every device: nothing can fire
      when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(List.of(disabledDevice));
      assertFalse(pwaNotificationService.canReceiveDirectNotifications(TEST_USER, "chat"));
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void fireDirectNotificationSkipsKindDisabledDuringDelay() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(false);
      PwaNotificationMessage message = new PwaNotificationMessage();
      message.setTitle("A title");
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, subscriptionId -> message);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);
      verify(executorService).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));

      // the user disabled the kind on this device during the delay window
      when(userPushSubscription.getNotificationSetting("chat")).thenReturn(new DeviceNotificationSetting(false, null));
      fire.getValue().run();
      verifyNoInteractions(pushService);
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void fireDirectNotificationAddsActionCredentialsOnlyWhenActionsAndSecret() throws Exception { // NOSONAR
    ScheduledExecutorService originalExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "executorService");
    ScheduledExecutorService originalDirectExecutor =
                                              (ScheduledExecutorService) ReflectionTestUtils.getField(pwaNotificationService,
                                                                                                      "directExecutorService");
    ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
    ReflectionTestUtils.setField(pwaNotificationService, "executorService", executorService);
    ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", executorService);
    try {
      when(pwaManifestService.isPwaEnabled()).thenReturn(true);
      mockSubscription(true);
      when(pwaNotificationTokenService.createToken(TEST_USER, "chat:!room:server", SUBSCRIPTION_ID)).thenReturn(PUSH_ACCESS_TOKEN);
      when(pushService.send(any())).thenReturn(httpResponse);
      when(httpResponse.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(201);

      PwaDirectNotificationBuilder builder = subscriptionId -> {
        PwaNotificationMessage message = new PwaNotificationMessage();
        message.setTitle("John in General");
        message.setTag("!room:server");
        message.setData(new HashMap<>(Map.of("roomId", "!room:server")));
        message.setActions(List.of(new PwaNotificationAction("Mark as read", "markRead")));
        return message;
      };
      pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, builder);
      ArgumentCaptor<Runnable> fire = ArgumentCaptor.forClass(Runnable.class);
      verify(executorService).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
      fire.getValue().run();

      ArgumentCaptor<Notification> pushCaptor = ArgumentCaptor.forClass(Notification.class);
      verify(pushService).send(pushCaptor.capture());
      String json = new String(pushCaptor.getValue().getPayload(), StandardCharsets.UTF_8);
      // the device gets a token scoped to (kind:tag, device) plus what it needs to prove itself
      assertTrue(json.contains("\"token\":\"" + PUSH_ACCESS_TOKEN + "\""));
      assertTrue(json.contains("\"objectId\":\"chat:!room:server\""));
      assertTrue(json.contains("\"kind\":\"chat\""));
      assertTrue(json.contains("\"subscriptionId\":\"" + SUBSCRIPTION_ID + "\""));
      assertTrue(json.contains("\"roomId\":\"!room:server\""));

      // no actions: no token minted, payload stays lean
      {
        PwaDirectNotificationBuilder plain = subscriptionId -> {
          PwaNotificationMessage message = new PwaNotificationMessage();
          message.setTitle("plain");
          message.setTag("!room:server");
          return message;
        };
        pwaNotificationService.scheduleDirectNotification(TEST_USER, "chat", 60l, plain);
        verify(executorService, times(2)).schedule(fire.capture(), eq(60l), eq(TimeUnit.SECONDS));
        fire.getValue().run();
        verify(pwaNotificationTokenService, times(1)).createToken(anyString(), anyString(), anyString());
      }
    } finally {
      ReflectionTestUtils.setField(pwaNotificationService, "executorService", originalExecutor);
      ReflectionTestUtils.setField(pwaNotificationService, "directExecutorService", originalDirectExecutor);
    }
  }

  @Test
  public void handleDirectNotificationActionAuthenticatesAndDispatches() throws Exception { // NOSONAR
    mockSubscription(true);
    String objectId = "chat:!room:server";
    long timestamp = System.currentTimeMillis();
    String proof = computeHmac(objectId, PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, timestamp);
    String header = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, timestamp, proof);
    when(pwaNotificationTokenService.validateToken(PUSH_ACCESS_TOKEN, objectId, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(pwaNotificationTokenService.consumeToken(PUSH_ACCESS_TOKEN, objectId, SUBSCRIPTION_ID)).thenReturn(TEST_USER);
    when(directNotificationActionPlugin.getNotificationKind()).thenReturn("chat");
    Map<String, String> data = Map.of("objectId", objectId, "roomId", "!room:server", "eventId", "$evt");

    pwaNotificationService.handleDirectNotificationAction("chat", "markRead", data, header);
    // the target handed to the plugin is the token-scoped object, not the echoed roomId
    verify(directNotificationActionPlugin).handleAction(TEST_USER, "markRead", "!room:server", data);
    verify(pwaNotificationTokenService).consumeToken(PUSH_ACCESS_TOKEN, objectId, SUBSCRIPTION_ID);

    // object of another kind, bad proof, missing object id
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.handleDirectNotificationAction("news", "markRead", data, header));
    String badHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, timestamp, "bad-proof");
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.handleDirectNotificationAction("chat", "markRead", data, badHeader));
    assertThrows(IllegalArgumentException.class,
                 () -> pwaNotificationService.handleDirectNotificationAction("chat", "markRead", Map.of("roomId", "!r"), header));

    // an unknown kind is rejected BEFORE the single-use token is touched
    Map<String, String> newsData = Map.of("objectId", "news:42");
    String newsHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, timestamp,
                                                 computeHmac("news:42", PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, timestamp));
    assertThrows(IllegalArgumentException.class,
                 () -> pwaNotificationService.handleDirectNotificationAction("news", "markRead", newsData, newsHeader));
    verify(pwaNotificationTokenService, never()).consumeToken(PUSH_ACCESS_TOKEN, "news:42", SUBSCRIPTION_ID);

    // validator negatives: missing header, expired timestamp, consumed by someone else
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.validateDirectNotificationAccess(objectId, null, false));
    int tokenTtlSeconds = (int) ReflectionTestUtils.getField(pwaNotificationService, "pushTokenTtlSeconds");
    long staleTimestamp = timestamp - TimeUnit.SECONDS.toMillis(tokenTtlSeconds) - 1000;
    String staleHeader = buildAuthorizationHeader(PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, staleTimestamp,
                                                  computeHmac(objectId, PUSH_ACCESS_TOKEN, SUBSCRIPTION_ID, staleTimestamp));
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.validateDirectNotificationAccess(objectId, staleHeader, false));
    when(pwaNotificationTokenService.consumeToken(PUSH_ACCESS_TOKEN, objectId, SUBSCRIPTION_ID)).thenReturn("someoneElse");
    assertThrows(IllegalAccessException.class,
                 () -> pwaNotificationService.validateDirectNotificationAccess(objectId, header, true));
  }

  @Test
  public void createSkipsPushExcludedPlugin() throws Exception { // NOSONAR
    assertFalse(pwaNotificationService.isPluginExcludedFromPush("ExcludedPlugin"));
    pwaNotificationService.excludePluginFromPush("ExcludedPlugin");
    assertTrue(pwaNotificationService.isPluginExcludedFromPush("ExcludedPlugin"));

    when(pwaManifestService.isPwaEnabled()).thenReturn(true);
    when(webNotificationService.getNotificationInfo(String.valueOf(NOTIFICATION_ID))).thenReturn(notification);
    when(notification.getKey()).thenReturn(PluginKey.key("ExcludedPlugin"));
    // everything below would let the push go out if the exclusion guard were
    // dropped, so its absence fails on the behavior, not on a missing stub
    lenient().when(notification.getTo()).thenReturn(TEST_USER);
    lenient().when(notification.getId()).thenReturn(String.valueOf(NOTIFICATION_ID));
    lenient().when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    lenient().when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    lenient().when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    lenient().when(pushService.send(any())).thenReturn(httpResponse);
    lenient().when(httpResponse.getStatusLine()).thenReturn(statusLine);
    lenient().when(statusLine.getStatusCode()).thenReturn(200);

    ScheduledFuture<?> future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verifyNoInteractions(pushService);
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

  private String computeHmac(String objectId, String token, String subscriptionId, long timestamp) throws Exception { // NOSONAR
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(PUSH_DEVICE_SECRET), "HmacSHA256"));
    String value = objectId + ":" + token + ":" + subscriptionId + ":" + timestamp;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
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
