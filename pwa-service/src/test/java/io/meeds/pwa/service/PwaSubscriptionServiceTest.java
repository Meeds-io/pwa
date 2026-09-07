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

import static io.meeds.pwa.service.PwaSubscriptionService.PWA_INSTALLED;
import static io.meeds.pwa.service.PwaSubscriptionService.PWA_UNINSTALLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.exoplatform.services.listener.ListenerService;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.DeviceNotificationSetting;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@SpringBootTest(classes = {
  PwaSubscriptionService.class,
})
public class PwaSubscriptionServiceTest {

  private static final String    SUBSCRIPTION_ID       = "subscriptionId";

  private static final String    SUBSCRIPTION_ENDPOINT = "http://localhost/endpoint";

  private static final String    PUSH_DEVICE_SECRET    = "deviceSecret";

  private static final String    NEW_SUBSCRIPTION_ID   = "newSubscriptionId";

  private static final String    TEST_USER             = "testUser";

  @MockitoBean
  private PwaSubscriptionStorage pwaSubscriptionStorage;

  @MockitoBean
  private ListenerService        listenerService;

  @Autowired
  private PwaSubscriptionService pwaSubscriptionService;

  @MockitoBean
  private UserPushSubscription   userPushSubscription;

  @Test
  public void getSubscriptions() {
    List<UserPushSubscription> subscriptions = pwaSubscriptionService.getSubscriptions(TEST_USER);
    assertNotNull(subscriptions);
    verify(pwaSubscriptionStorage).get(TEST_USER);
  }

  @Test
  public void createSubscriptionWhenExists() {
    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    pwaSubscriptionService.createSubscription(userPushSubscription, TEST_USER);
    verify(pwaSubscriptionStorage, never()).create(userPushSubscription, TEST_USER);

    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.emptyList());
    pwaSubscriptionService.createSubscription(userPushSubscription, TEST_USER);
    verify(pwaSubscriptionStorage).create(userPushSubscription, TEST_USER);
    verify(listenerService).broadcast(PWA_INSTALLED, TEST_USER, userPushSubscription);
  }

  @Test
  public void deleteSubscription() {
    when(pwaSubscriptionStorage.delete(SUBSCRIPTION_ID, TEST_USER)).thenReturn(userPushSubscription);
    pwaSubscriptionService.deleteSubscription(SUBSCRIPTION_ID, TEST_USER);
    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ID, TEST_USER);
    verify(listenerService).broadcast(PWA_UNINSTALLED, TEST_USER, userPushSubscription);
  }

  @Test
  public void deleteAllSubscriptions() {
    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    pwaSubscriptionService.deleteAllSubscriptions(TEST_USER);
    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ID, TEST_USER);
  }


  @Test
  public void getSubscriptionShouldReturnMatchingSubscription() {
    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);

    UserPushSubscription result = pwaSubscriptionService.getSubscription(TEST_USER, SUBSCRIPTION_ID);

    assertEquals(userPushSubscription, result);
    assertNull(pwaSubscriptionService.getSubscription(TEST_USER, "unknown"));
  }

  @Test
  public void createSubscriptionShouldUpdateExistingSubscriptionWhenSecretChanges() {
    UserPushSubscription newSubscription = new UserPushSubscription();
    newSubscription.setId(SUBSCRIPTION_ID);
    newSubscription.setEndpoint(SUBSCRIPTION_ENDPOINT);
    newSubscription.setPushDeviceSecret(PUSH_DEVICE_SECRET);

    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(userPushSubscription.getPushDeviceSecret()).thenReturn(null);

    pwaSubscriptionService.createSubscription(newSubscription, TEST_USER);

    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ID, TEST_USER);
    verify(pwaSubscriptionStorage).create(newSubscription, TEST_USER);
    verify(listenerService, never()).broadcast(PWA_INSTALLED, TEST_USER, newSubscription);
  }

  @Test
  public void createSubscriptionShouldUpdateExistingSubscriptionWhenIdChanges() {
    UserPushSubscription newSubscription = new UserPushSubscription();
    newSubscription.setId(NEW_SUBSCRIPTION_ID);
    newSubscription.setEndpoint(SUBSCRIPTION_ENDPOINT);
    newSubscription.setPushDeviceSecret(PUSH_DEVICE_SECRET);

    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(userPushSubscription.getPushDeviceSecret()).thenReturn(PUSH_DEVICE_SECRET);

    pwaSubscriptionService.createSubscription(newSubscription, TEST_USER);

    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ID, TEST_USER);
    verify(pwaSubscriptionStorage).create(newSubscription, TEST_USER);
  }

  @Test
  public void createSubscriptionShouldCarryStoredNotificationSettingsOverRecreate() {
    UserPushSubscription newSubscription = new UserPushSubscription();
    newSubscription.setId(NEW_SUBSCRIPTION_ID);
    newSubscription.setEndpoint(SUBSCRIPTION_ENDPOINT);
    newSubscription.setPushDeviceSecret(PUSH_DEVICE_SECRET);

    // a client-injected settings map must be discarded, not stored
    newSubscription.setNotificationSetting("chat", new DeviceNotificationSetting(true, 999));

    Map<String, DeviceNotificationSetting> storedSettings = Map.of("chat", new DeviceNotificationSetting(false, 15));
    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(userPushSubscription));
    when(userPushSubscription.getEndpoint()).thenReturn(SUBSCRIPTION_ENDPOINT);
    when(userPushSubscription.getId()).thenReturn(SUBSCRIPTION_ID);
    when(userPushSubscription.getPushDeviceSecret()).thenReturn(PUSH_DEVICE_SECRET);
    when(userPushSubscription.getNotificationSettings()).thenReturn(storedSettings);

    pwaSubscriptionService.createSubscription(newSubscription, TEST_USER);

    // the client subscribe body never carries settings: the stored ones survive
    verify(pwaSubscriptionStorage).create(newSubscription, TEST_USER);
    assertEquals(storedSettings, newSubscription.getNotificationSettings());
  }

  @Test
  public void createSubscriptionDiscardsClientSentNotificationSettings() {
    UserPushSubscription newSubscription = new UserPushSubscription();
    newSubscription.setId(SUBSCRIPTION_ID);
    newSubscription.setEndpoint(SUBSCRIPTION_ENDPOINT);
    newSubscription.setNotificationSetting("chat", new DeviceNotificationSetting(false, 999));

    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.emptyList());
    pwaSubscriptionService.createSubscription(newSubscription, TEST_USER);

    // fresh create: nothing client-sent reaches the storage
    verify(pwaSubscriptionStorage).create(newSubscription, TEST_USER);
    assertNull(newSubscription.getNotificationSettings());
  }

  @Test
  public void getAndSaveNotificationSetting() throws Exception {
    UserPushSubscription subscription = new UserPushSubscription();
    subscription.setId(SUBSCRIPTION_ID);
    when(pwaSubscriptionStorage.get(TEST_USER)).thenReturn(Collections.singletonList(subscription));

    assertNull(pwaSubscriptionService.getNotificationSetting(TEST_USER, SUBSCRIPTION_ID, "chat"));
    assertThrows(ObjectNotFoundException.class,
                 () -> pwaSubscriptionService.getNotificationSetting(TEST_USER, "unknown", "chat"));
    assertThrows(ObjectNotFoundException.class,
                 () -> pwaSubscriptionService.saveNotificationSetting(TEST_USER,
                                                                      "unknown",
                                                                      "chat",
                                                                      new DeviceNotificationSetting(true, 5)));

    DeviceNotificationSetting setting = new DeviceNotificationSetting(true, 10);
    pwaSubscriptionService.saveNotificationSetting(TEST_USER, SUBSCRIPTION_ID, "chat", setting);
    verify(pwaSubscriptionStorage).create(subscription, TEST_USER);
    assertEquals(setting, pwaSubscriptionService.getNotificationSetting(TEST_USER, SUBSCRIPTION_ID, "chat"));

    // a kind is a short technical key; the delay is bounded to one day
    assertThrows(IllegalArgumentException.class,
                 () -> pwaSubscriptionService.saveNotificationSetting(TEST_USER, SUBSCRIPTION_ID, "chat", null));
    assertThrows(IllegalArgumentException.class,
                 () -> pwaSubscriptionService.saveNotificationSetting(TEST_USER,
                                                                      SUBSCRIPTION_ID,
                                                                      "invalid kind!",
                                                                      new DeviceNotificationSetting(true, 5)));
    assertThrows(IllegalArgumentException.class,
                 () -> pwaSubscriptionService.saveNotificationSetting(TEST_USER,
                                                                      SUBSCRIPTION_ID,
                                                                      "chat",
                                                                      new DeviceNotificationSetting(true, 0)));
    assertThrows(IllegalArgumentException.class,
                 () -> pwaSubscriptionService.saveNotificationSetting(TEST_USER,
                                                                      SUBSCRIPTION_ID,
                                                                      "chat",
                                                                      new DeviceNotificationSetting(true, 2000)));
  }

  @Test
  public void deleteSubscriptionShouldNotBroadcastWhenUserActionIsFalse() {
    when(pwaSubscriptionStorage.delete(SUBSCRIPTION_ID, TEST_USER)).thenReturn(userPushSubscription);

    pwaSubscriptionService.deleteSubscription(SUBSCRIPTION_ID, TEST_USER, false);

    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ID, TEST_USER);
    verifyNoInteractions(listenerService);
  }

}
