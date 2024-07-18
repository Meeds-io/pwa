/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2022 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.pwa.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@Service
public class PwaSubscriptionService {

  private static final Log       LOG = ExoLogger.getLogger(PwaSubscriptionService.class);

  @Autowired
  private PwaSubscriptionStorage pwaSubscriptionStorage;

  public List<UserPushSubscription> getSubscriptions(String username) {
    return pwaSubscriptionStorage.get(username);
  }

  public void createSubscription(UserPushSubscription subscription, String username) {
    List<UserPushSubscription> subscriptions = pwaSubscriptionStorage.get(username);
    if (subscriptions.stream().noneMatch(s -> StringUtils.equals(s.getEndpoint(), subscription.getEndpoint()))) {
      LOG.info("Create new subscription with id {} for user {} and endpoint {}",
               subscription.getId(),
               username,
               subscription.getEndpoint());
      pwaSubscriptionStorage.create(subscription, username);
    } else {
      LOG.info("Subscription for endpoint {} already exists for user {}", subscription.getEndpoint(), username);
    }
  }

  public void deleteSubscription(String id, String username) {
    pwaSubscriptionStorage.delete(id, username);
    LOG.info("Delete subscription with id {} for user {}", id, username);
  }

  public void deleteAllSubscriptions(String username) {
    pwaSubscriptionStorage.deleteAll(username);
    LOG.info("Delete all subscriptions for user {}", username);
  }

}
