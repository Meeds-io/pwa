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
import static io.meeds.pwa.service.PwaNotificationService.EVENT_ACTION_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_ERROR_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_HTTP_RESPONSE_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_NOTIFICATION_ID_PARAM_NAME;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_NOTIFICATION_RESPONSE_ERROR;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_NOTIFICATION_SENDING_ERROR;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_NOTIFICATION_SENT;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_OUTDATED_SUBSCRIPTION;
import static io.meeds.pwa.service.PwaNotificationService.EVENT_SUBSCRIPTION_PARAM_NAME;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.pwa.model.UserPushSubscription;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class PwaNotificationListener extends BasePwaStatisticCollector implements ListenerBase<String, Map<String, Object>> {

  private static final Log          LOG         = ExoLogger.getLogger(PwaNotificationListener.class);

  private static final List<String> EVENT_NAMES = Arrays.asList(EVENT_NOTIFICATION_SENT,
                                                                EVENT_NOTIFICATION_RESPONSE_ERROR,
                                                                EVENT_NOTIFICATION_SENDING_ERROR,
                                                                EVENT_OUTDATED_SUBSCRIPTION);

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  public void onEvent(Event<String, Map<String, Object>> event) throws Exception {
    String eventName = event.getEventName();
    String username = event.getSource();
    Map<String, Object> params = event.getData();

    UserPushSubscription subscription = (UserPushSubscription) params.get(EVENT_SUBSCRIPTION_PARAM_NAME);
    StatisticData statisticData = addSubscriptionDetails(subscription, username);
    statisticData.setOperation(getOperation(eventName));
    statisticData.setStatus(EVENT_NOTIFICATION_SENT.equals(eventName) ? StatisticStatus.OK : StatisticStatus.KO);

    HttpResponse httpResponse = (HttpResponse) params.get(EVENT_HTTP_RESPONSE_PARAM_NAME);
    String errorMessage = (String) params.get(EVENT_ERROR_PARAM_NAME);
    addErrorMessage(eventName, statisticData, httpResponse, errorMessage);
    addHttpResponseCode(statisticData, httpResponse);
    addNotificationAction(statisticData, (String) params.get(EVENT_ACTION_PARAM_NAME));
    addNotificationId(statisticData, (Long) params.get(EVENT_NOTIFICATION_ID_PARAM_NAME));
    addStatisticData(statisticData);
  }

  private void addHttpResponseCode(StatisticData statisticData, HttpResponse httpResponse) {
    if (httpResponse != null && httpResponse.getStatusLine() != null) {
      statisticData.addParameter("pwa.httpResponseCode", httpResponse.getStatusLine().getStatusCode());
    }
  }

  private void addNotificationId(StatisticData statisticData, Long notificationId) {
    if (notificationId != null) {
      statisticData.addParameter("pwa.notificationId", notificationId);
    }
  }

  private void addNotificationAction(StatisticData statisticData, String action) {
    if (StringUtils.isNotBlank(action)) {
      statisticData.addParameter("pwa.action", action);
    }
  }

  private void addErrorMessage(String eventName, StatisticData statisticData, HttpResponse httpResponse, String errorMessage) {
    if (httpResponse != null && StringUtils.equals(EVENT_NOTIFICATION_RESPONSE_ERROR, eventName)) {
      errorMessage = getErrorMessage(httpResponse);
    }
    if (StringUtils.isNotBlank(errorMessage)) {
      statisticData.addParameter("pwa.errorMessage", errorMessage);
    }
  }

  private String getErrorMessage(HttpResponse httpResponse) {
    if (httpResponse.getEntity() != null) {
      try (InputStream inputStream = httpResponse.getEntity().getContent()) {
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      } catch (Exception e) {
        LOG.warn("Error while retrieving HTTP Response content", e);
      }
    }
    return null;
  }

  private String getOperation(String eventName) {
    return switch (eventName) {
    case EVENT_NOTIFICATION_SENT: {
      yield "pwa.notification.sent";
    }
    case EVENT_NOTIFICATION_RESPONSE_ERROR: {
      yield "pwa.notification.pushServerError";
    }
    case EVENT_NOTIFICATION_SENDING_ERROR: {
      yield "pwa.notification.internalError";
    }
    case EVENT_OUTDATED_SUBSCRIPTION: {
      yield "pwa.notification.subscriptionOutdated";
    }
    default:
      throw new IllegalArgumentException("Unexpected event name value: " + eventName);
    };
  }

}
