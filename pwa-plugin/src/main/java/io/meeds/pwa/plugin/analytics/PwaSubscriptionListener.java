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

import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.pwa.service.PwaSubscriptionService.PWA_INSTALLED;
import static io.meeds.pwa.service.PwaSubscriptionService.PWA_UNINSTALLED;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.common.ContainerTransactional;
import io.meeds.pwa.model.UserPushSubscription;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class PwaSubscriptionListener extends BasePwaStatisticCollector implements ListenerBase<String, UserPushSubscription> {

  private static final List<String> EVENT_NAMES = Arrays.asList(PWA_INSTALLED, PWA_UNINSTALLED);

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  @ContainerTransactional
  public void onEvent(Event<String, UserPushSubscription> event) throws Exception {
    String username = event.getSource();
    UserPushSubscription subscription = event.getData();
    StatisticData statisticData = addSubscriptionDetails(subscription, username);
    statisticData.setOperation(getOperation(event.getEventName()));
    statisticData.setStatus(StatisticStatus.OK);
    addStatisticData(statisticData);
  }

  private String getOperation(String eventName) {
    return switch (eventName) {
    case PWA_INSTALLED: {
      yield "installed";
    }
    case PWA_UNINSTALLED: {
      yield "uninstalled";
    }
    default:
      throw new IllegalArgumentException("Unexpected event name value: " + eventName);
    };
  }

}
