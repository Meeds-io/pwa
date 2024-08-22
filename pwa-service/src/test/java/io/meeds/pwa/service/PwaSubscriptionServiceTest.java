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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.exoplatform.services.listener.ListenerService;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@SpringBootTest(classes = {
                            PwaSubscriptionService.class,
})
public class PwaSubscriptionServiceTest {

  private static final String    SUBSCRIPTION_ID       = "subscriptionId";

  private static final String    SUBSCRIPTION_ENDPOINT = "http://localhost/endpoint";

  private static final String    TEST_USER             = "testUser";

  @MockBean
  private PwaSubscriptionStorage pwaSubscriptionStorage;

  @MockBean
  private ListenerService        listenerService;

  @Autowired
  private PwaSubscriptionService pwaSubscriptionService;

  @Mock
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

}
