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

import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_CLOSE_UI_ACTION;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_MARK_READ_USER_ACTION;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_OPEN_UI_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
import nl.martijndwars.webpush.PushService;

@SpringBootTest(classes = {
                            PwaNotificationService.class,
})
public class PwaNotificationServiceTest {

  private static final String          SUBSCRIPTION_ID       = "subscriptionId";

  private static final String          SUBSCRIPTION_ENDPOINT = "http://localhost/endpoint";

  private static final PluginKey       PLUGIN_KEY            = PluginKey.key("TestPlugin");

  private static final long            NOTIFICATION_ID       = 12l;

  private static final String          TEST_USER             = "testUser";

  @MockBean
  private PwaManifestService           pwaManifestService;

  @MockBean
  private PwaSubscriptionService       pwaSubscriptionService;

  @MockBean
  private PwaNotificationStorage       pwaNotificationStorage;

  @MockBean
  private WebNotificationService       webNotificationService;

  @MockBean
  private ListenerService              listenerService;

  @MockBean
  private OrganizationService          organizationService;

  @MockBean
  private LocaleConfigService          localeConfigService;

  @MockBean
  private ResourceBundleService        resourceBundleService;

  @MockBean
  private DefaultPwaNotificationPlugin defaultPwaNotificationPlugin;

  @MockBean
  private PushService                  pushService;

  @Autowired
  private PwaNotificationService       pwaNotificationService;

  @Mock
  private NotificationInfo             notification;

  @Mock
  private UserProfile                  userProfile;

  @Mock
  private UserProfileHandler           userProfileHandler;

  @Mock
  private PwaNotificationMessage       notificationMessage;

  @Mock
  private UserPushSubscription         userPushSubscription;

  @Mock
  private HttpResponse                 httpResponse;

  @Mock
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
    when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(pushService.send(any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(401);

    future = pwaNotificationService.create(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService, never()).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
    verify(pushService).send(argThat(n -> (NOTIFICATION_ID + ":" +
        PWA_NOTIFICATION_OPEN_UI_ACTION).equals(new String(n.getPayload()))));

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
  public void delete() throws Exception { // NOSONAR
    ScheduledFuture<?> future = pwaNotificationService.delete(NOTIFICATION_ID);
    assertNull(future);
    when(pwaManifestService.isPwaEnabled()).thenReturn(true);
    future = pwaNotificationService.delete(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verifyNoInteractions(listenerService);

    mockWebNotification();
    when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(pushService.send(any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(401);

    future = pwaNotificationService.delete(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService, never()).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
    verify(pushService).send(argThat(n -> (NOTIFICATION_ID + ":" +
        PWA_NOTIFICATION_CLOSE_UI_ACTION).equals(new String(n.getPayload()))));

    when(statusLine.getStatusCode()).thenReturn(410);
    future = pwaNotificationService.delete(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);

    when(statusLine.getStatusCode()).thenReturn(200);
    future = pwaNotificationService.delete(NOTIFICATION_ID);
    assertNotNull(future);
    assertEquals(1, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
  }

  @Test
  public void deleteAll() throws Exception { // NOSONAR
    ScheduledFuture<?> future = pwaNotificationService.deleteAll(TEST_USER);
    assertNull(future);
    when(pwaManifestService.isPwaEnabled()).thenReturn(true);
    future = pwaNotificationService.deleteAll(TEST_USER);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verifyNoInteractions(listenerService);

    mockWebNotification();
    when(pwaSubscriptionService.getSubscriptions(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(pushService.send(any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(401);

    future = pwaNotificationService.deleteAll(TEST_USER);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService, never()).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
    verify(pushService).send(argThat(n -> new String(n.getPayload()).contains(":" + PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION)));

    when(statusLine.getStatusCode()).thenReturn(410);
    future = pwaNotificationService.deleteAll(TEST_USER);
    assertNotNull(future);
    assertEquals(0, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);

    when(statusLine.getStatusCode()).thenReturn(200);
    future = pwaNotificationService.deleteAll(TEST_USER);
    assertNotNull(future);
    assertEquals(1, (int) future.get());
    verify(pwaSubscriptionService).deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);
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
  }

  private void mockWebNotificationNoAccess() {
    when(webNotificationService.getNotificationInfo(String.valueOf(NOTIFICATION_ID))).thenReturn(notification);
    when(notification.getKey()).thenReturn(PLUGIN_KEY);
  }
}
