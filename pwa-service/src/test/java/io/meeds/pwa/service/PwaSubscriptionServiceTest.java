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

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@SpringBootTest(classes = {
                            PwaSubscriptionService.class,
})
public class PwaSubscriptionServiceTest {

  private static final String    SUBSCRIPTION_ENDPOINT_ID = "subscriptionId";

  private static final String    SUBSCRIPTION_ENDPOINT    = "http://localhost/endpoint";

  private static final String    TEST_USER                = "testUser";

  @MockBean
  private PwaSubscriptionStorage pwaSubscriptionStorage;

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
  }

  @Test
  public void deleteSubscription() {
    pwaSubscriptionService.deleteSubscription(SUBSCRIPTION_ENDPOINT_ID, TEST_USER);
    verify(pwaSubscriptionStorage).delete(SUBSCRIPTION_ENDPOINT_ID, TEST_USER);
  }

  @Test
  public void deleteAllSubscriptions() {
    pwaSubscriptionService.deleteAllSubscriptions(TEST_USER);
    verify(pwaSubscriptionStorage).deleteAll(TEST_USER);
  }

}
