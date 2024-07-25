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

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@Service
public class PwaSubscriptionService {

  public static final String     PWA_INSTALLED   = "pwa.installed";

  public static final String     PWA_UNINSTALLED = "pwa.uninstalled";

  private static final Log       LOG             = ExoLogger.getLogger(PwaSubscriptionService.class);

  @Autowired
  private PwaSubscriptionStorage pwaSubscriptionStorage;

  @Autowired
  private ListenerService        listenerService;

  public List<UserPushSubscription> getSubscriptions(String username) {
    return pwaSubscriptionStorage.get(username);
  }

  public void createSubscription(UserPushSubscription subscription,
                                 String username) {
    List<UserPushSubscription> subscriptions = pwaSubscriptionStorage.get(username);
    String endpoint = subscription.getEndpoint();
    if (subscriptions.stream().noneMatch(s -> StringUtils.equals(s.getEndpoint(), endpoint))) {
      LOG.info("Create new subscription with id {} for user {} and endpoint {}",
               subscription.getId(),
               username,
               getSubscriptionDomain(endpoint));
      pwaSubscriptionStorage.create(subscription, username);
      listenerService.broadcast(PWA_INSTALLED, username, subscription);
    } else {
      LOG.debug("Subscription for endpoint {} already exists for user {}", getSubscriptionDomain(endpoint), username);
    }
  }

  public void deleteSubscription(String id, String username) {
    deleteSubscription(id, username, true);
  }

  public void deleteSubscription(String id, String username, boolean userAction) {
    UserPushSubscription subscription = pwaSubscriptionStorage.delete(id, username);
    if (userAction && subscription != null) {
      listenerService.broadcast(PWA_UNINSTALLED, username, subscription);
    }
  }

  public void deleteAllSubscriptions(String username) {
    List<UserPushSubscription> subscriptions = getSubscriptions(username);
    subscriptions.forEach(s -> deleteSubscription(s.getId(), username));
  }

  private String getSubscriptionDomain(String endpoint) {
    return endpoint.substring(0, endpoint.indexOf("/", 15));
  }

}
