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
package io.meeds.pwa.listener;

import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_DELETED_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_READ_ALL_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_READ_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_SAVED_EVENT;

import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.pwa.service.PwaNotificationService;

import jakarta.annotation.PostConstruct;

@Component
public class WebNotificationSentListener implements ListenerBase<Object, Object> {

  private static final Log       LOG         = ExoLogger.getLogger(WebNotificationSentListener.class);

  private static final String[]  EVENT_NAMES = new String[] {
                                                              NOTIFICATION_WEB_SAVED_EVENT,
                                                              NOTIFICATION_WEB_DELETED_EVENT,
                                                              NOTIFICATION_WEB_READ_EVENT,
                                                              NOTIFICATION_WEB_READ_ALL_EVENT
  };

  @Autowired
  private PwaNotificationService pwaNotificationService;

  @Autowired
  private ListenerService        listenerService;

  @PostConstruct
  protected void init() {
    Stream.of(EVENT_NAMES)
          .forEach(eventName -> listenerService.addListener(eventName, this));
  }

  @Override
  public void onEvent(Event<Object, Object> event) throws Exception {
    try {
      switch (event.getEventName()) {
      case NOTIFICATION_WEB_SAVED_EVENT: {
        Boolean isNew = (Boolean) event.getData();
        String notificationId = (String) event.getSource();
        if (isNew != null
            && isNew.booleanValue()
            && notificationId != null) {
          pwaNotificationService.create(Long.parseLong(notificationId));
        }
        break;
      }
      case NOTIFICATION_WEB_DELETED_EVENT, NOTIFICATION_WEB_READ_EVENT: {
        String notificationId = (String) event.getSource();
        if (notificationId != null) {
          pwaNotificationService.delete(Long.parseLong(notificationId));
        }
        break;
      }
      case NOTIFICATION_WEB_READ_ALL_EVENT: {
        String username = (String) event.getSource();
        if (username != null) {
          pwaNotificationService.deleteAll(username);
        }
        break;
      }
      default:
        throw new IllegalArgumentException("Event not handled: " + event.getEventName());
      }
    } catch (Exception e) {
      LOG.warn("Error while handling notification event '{}' with id '{}'", event.getEventName(), event.getSource(), e);
    }
  }

}