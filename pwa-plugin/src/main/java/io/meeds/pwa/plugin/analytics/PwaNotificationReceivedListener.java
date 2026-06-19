/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_DELAY_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_RECEIVED_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_PUSH_SENT_TIME;
import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_RECEIVED;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.pwa.model.UserPushSubscription;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class PwaNotificationReceivedListener extends BasePwaStatisticCollector
    implements ListenerBase<UserPushSubscription, NotificationInfo> {

  private static final List<String> EVENT_NAMES = Arrays.asList(PWA_NOTIFICATION_RECEIVED);

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  public void onEvent(Event<UserPushSubscription, NotificationInfo> event) throws Exception {
    NotificationInfo notificationInfo = event.getData();
    UserPushSubscription subscription = event.getSource();

    StatisticData statisticData = addSubscriptionDetails(subscription, notificationInfo.getTo());
    statisticData.setSubModule("pwaNotification");
    statisticData.setOperation("pwaNotificationPushNotificationReceived");
    statisticData.setStatus(StatisticStatus.OK);
    statisticData.setDuration(Long.parseLong(notificationInfo.getOwnerParameter().get(PWA_NOTIFICATION_PUSH_DELAY_TIME)));

    statisticData.addKeyword("pwaNotificationId", Long.parseLong(notificationInfo.getId()));
    statisticData.addLong("pwaNotificationSentAt",
                          notificationInfo.getOwnerParameter().get(PWA_NOTIFICATION_PUSH_SENT_TIME));
    statisticData.addLong("pwaNotificationClientReceivedAt",
                          notificationInfo.getOwnerParameter().get(PWA_NOTIFICATION_PUSH_RECEIVED_TIME));
    statisticData.addLong("pwaNotificationServerReceivedAt",
                          notificationInfo.getOwnerParameter()
                                                         .get(PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME));
    addStatisticData(statisticData);
  }

}
