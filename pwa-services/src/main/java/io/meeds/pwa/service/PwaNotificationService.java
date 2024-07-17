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

import java.security.KeyPair;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaNotificationStorage;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class PwaNotificationService {

  private static final String    PWA_NOTIFICATION_CREATED       = "pwa.notification.created";

  private static final String    PWA_NOTIFICATION_DELETED       = "pwa.notification.deleted";

  private static final String    PWA_NOTIFICATION_ALL_DELETED   = "pwa.notification.allDeleted";

  private static final String    PWA_NOTIFICATION_DELETE_ALL_ID = "0";

  private static final Log       LOG                            = ExoLogger.getLogger(PwaNotificationService.class);

  @Autowired
  private PwaSubscriptionService pwaSubscriptionService;

  @Autowired
  private PwaNotificationStorage pwaNotificationStorage;

  @Autowired
  private WebNotificationService webNotificationService;

  @Autowired
  private ListenerService        listenerService;

  private PushService            pushService;

  @Value("${pwa.notifications.enabled:true}")
  private boolean                enabled;

  private Queue<Long>            webNotificationsDeleteQueue    = new ConcurrentLinkedQueue<>();

  private Queue<Long>            webNotificationsCreateQueue    = new ConcurrentLinkedQueue<>();

  private Queue<String>          webNotificationsDeleteAllQueue = new ConcurrentLinkedQueue<>();

  /**
   * Send a Push Notification to display to user device(s)
   * 
   * @param webNotificationId
   */
  public void create(long webNotificationId) {
    if (webNotificationsCreateQueue.offer(webNotificationId)) {
      listenerService.broadcast(PWA_NOTIFICATION_CREATED, webNotificationId, null);
    } else {
      LOG.warn("Web notification with id {} wasn't considered as created", webNotificationId);
    }
  }

  /**
   * Delete a previously displayed Push Notification to End user due if not
   * dismissed yet
   * 
   * @param webNotificationId
   */
  public void delete(long webNotificationId) {
    if (webNotificationsDeleteQueue.offer(webNotificationId)) {
      listenerService.broadcast(PWA_NOTIFICATION_DELETED, webNotificationId, null);
    } else {
      LOG.warn("Web notification with id {} wasn't considered as deleted", webNotificationId);
    }
  }

  /**
   * Delete all previously displayed push notifications to user's device(s)
   * 
   * @param username
   */
  public void deleteAll(String username) {
    if (webNotificationsDeleteAllQueue.offer(username)) {
      listenerService.broadcast(PWA_NOTIFICATION_ALL_DELETED, username, null);
    } else {
      LOG.warn("Web notification for user {} wasn't considered to consider all its web notification as deleted", username);
    }
  }

  public void processNotifications() {
    int toCreateSize = webNotificationsCreateQueue.size();
    int toDeleteSize = webNotificationsDeleteQueue.size();
    int toAllDeleteSize = webNotificationsDeleteAllQueue.size();
    int totalSize = toCreateSize + toDeleteSize + toAllDeleteSize;
    if (totalSize > 0) {
      long start = System.currentTimeMillis();
      LOG.info("Process PWA Push notifications: {} to create, {} to delete, {} to all delete",
               toCreateSize,
               toDeleteSize,
               toAllDeleteSize);
      while (!webNotificationsCreateQueue.isEmpty()) {
        long webNotificationId = webNotificationsCreateQueue.poll();
        NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
        sendNotification(notification);
      }
      while (!webNotificationsDeleteQueue.isEmpty()) {
        long webNotificationId = webNotificationsCreateQueue.poll();
        NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
        sendNotification(notification);
      }
      Set<String> processedUserIds = new HashSet<>();
      while (!webNotificationsDeleteAllQueue.isEmpty()) {
        String username = webNotificationsDeleteAllQueue.poll();
        if (!processedUserIds.contains(username)) {
          processedUserIds.add(username);
          sendNotification(username, PWA_NOTIFICATION_DELETE_ALL_ID);
        }
      }
      LOG.info("Process PWA Push {} notifications processed in {}ms", totalSize, System.currentTimeMillis() - start);
    } else {
      LOG.info("No PWA Push notifications to process");
    }
  }

  private void sendNotification(NotificationInfo notification) {
    String username = notification.getTo();
    String notificationId = notification.getId();
    sendNotification(notificationId, username);
  }

  private void sendNotification(String notificationId, String username) {
    pwaSubscriptionService.getSubscriptions(username)
                          .forEach(subscription -> {
                            try {
                              sendPushMessage(subscription, notificationId.getBytes());
                            } catch (Exception e) {
                              LOG.warn("Error while sending push notification {} to user {}. Ignore reattempting and continue processing messages queue.",
                                       notificationId,
                                       username,
                                       e);
                            }
                          });
  }

  private void sendPushMessage(UserPushSubscription sub, byte[] payload) throws Exception { // NOSONAR
    Notification notification = new Notification(
                                                 sub.getEndpoint(),
                                                 sub.userPublicKey(),
                                                 sub.authAsBytes(),
                                                 payload);
    // Send the notification
    getPushService().send(notification);
  }

  private PushService getPushService() throws Exception { // NOSONAR
    if (pushService == null) {
      pushService = new PushService(new KeyPair(pwaNotificationStorage.getVapidPublicKey(),
                                                pwaNotificationStorage.getVapidPrivateKey()));
    }
    return pushService;
  }
}
