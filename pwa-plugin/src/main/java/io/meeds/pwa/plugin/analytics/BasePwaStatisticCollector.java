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
package io.meeds.pwa.plugin.analytics;

import org.springframework.beans.factory.annotation.Autowired;

import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.analytics.model.StatisticData;
import io.meeds.pwa.model.UserPushSubscription;

public abstract class BasePwaStatisticCollector {

  @Autowired
  private IdentityManager identityManager;

  protected StatisticData addSubscriptionDetails(UserPushSubscription subscription, String username) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("PWA");
    statisticData.setUserId(Long.parseLong(getIdentityId(username)));
    statisticData.addParameter("pwaDeviceType", subscription.getDeviceType());
    statisticData.addParameter("pwaSubscriptionId", subscription.getId());
    statisticData.addParameter("pwaSubscriptionSite", getSubscriptionDomain(subscription.getEndpoint()));
    return statisticData;
  }

  private String getIdentityId(String username) {
    return identityManager.getOrCreateUserIdentity(username).getId();
  }

  private String getSubscriptionDomain(String endpoint) {
    return endpoint.substring(0, endpoint.indexOf("/", 15));
  }

}
