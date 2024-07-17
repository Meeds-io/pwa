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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.plugin.DefaultPwaNotificationPlugin;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import io.meeds.pwa.storage.PwaNotificationStorage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class PwaNotificationService {

  private static final String                   PWA_NOTIFICATION_CREATED               = "pwa.notification.created";

  private static final String                   PWA_NOTIFICATION_DELETED               = "pwa.notification.deleted";

  private static final String                   PWA_NOTIFICATION_ALL_DELETED           = "pwa.notification.allDeleted";

  private static final String                   PWA_NOTIFICATION_OPEN_UI_ACTION        = "open";

  private static final String                   PWA_NOTIFICATION_CLOSE_UI_ACTION       = "close";

  private static final String                   PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION   = "closeAll";

  private static final String                   PWA_NOTIFICATION_MARK_READ_USER_ACTION = "markRead";

  private static final Random                   RANDOM                                 = new Random();

  private static final Log                      LOG                                    =
                                                    ExoLogger.getLogger(PwaNotificationService.class);

  @Autowired
  private PwaSubscriptionService                pwaSubscriptionService;

  @Autowired
  private PwaNotificationStorage                pwaNotificationStorage;

  @Autowired
  private WebNotificationService                webNotificationService;

  @Autowired
  private ListenerService                       listenerService;

  @Autowired
  private DefaultPwaNotificationPlugin          defaultPwaNotificationPlugin;

  private PushService                           pushService;

  @Value("${pwa.notifications.enabled:true}")
  private boolean                               enabled;

  @Value("${pwa.notifications.pool.size:5}")
  private int                                   poolSize;

  private Map<PluginKey, PwaNotificationPlugin> plugins                                = new ConcurrentHashMap<>();

  private ScheduledExecutorService              executorService;

  @PostConstruct
  public void init() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("PWA-Push-Notification-%d")
                                                            .build();
    executorService = Executors.newScheduledThreadPool(poolSize, threadFactory);
  }

  @PreDestroy
  public void destroy() {
    executorService.shutdown();
  }

  public PwaNotificationMessage getNotification(long webNotificationId, String username) throws ObjectNotFoundException,
                                                                                         IllegalAccessException {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      throw new ObjectNotFoundException(String.format("Notification with id %s doesn't exists", webNotificationId));
    } else if (!StringUtils.equals(notification.getTo(), username)) {
      throw new IllegalAccessException(String.format("Notification with id %s access denied", webNotificationId));
    }
    PwaNotificationPlugin notificationPlugin = plugins.get(notification.getKey());
    if (notificationPlugin == null) {
      notificationPlugin = defaultPwaNotificationPlugin;
    }
    return notificationPlugin.process(notification);
  }

  public void updateNotification(long webNotificationId, String action, String username) throws ObjectNotFoundException,
                                                                                         IllegalAccessException {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      throw new ObjectNotFoundException(String.format("Notification with id %s doesn't exists", webNotificationId));
    } else if (!StringUtils.equals(notification.getTo(), username)) {
      throw new IllegalAccessException(String.format("Notification with id %s access denied", webNotificationId));
    }
    if (StringUtils.equals(action, PWA_NOTIFICATION_MARK_READ_USER_ACTION)) {
      webNotificationService.markRead(String.valueOf(webNotificationId));
    }
  }

  /**
   * Send a Push Notification to display to user device(s)
   * 
   * @param webNotificationId
   */
  public void create(long webNotificationId) {
    executorService.schedule(() -> this.sendCreateNotification(webNotificationId), 1, TimeUnit.SECONDS);
  }

  /**
   * Delete a previously displayed Push Notification to End user due if not
   * dismissed yet
   * 
   * @param webNotificationId
   */
  public void delete(long webNotificationId) {
    executorService.schedule(() -> this.sendCloseNotification(webNotificationId), 1, TimeUnit.SECONDS);
  }

  /**
   * Delete all previously displayed push notifications to user's device(s)
   * 
   * @param username
   */
  public void deleteAll(String username) {
    executorService.schedule(() -> this.sendCloseAllNotifications(username), 1, TimeUnit.SECONDS);
  }

  /**
   * @return VAPID Public Key encoded using Base64url
   */
  public String getVapidPublicKeyString() {
    return pwaNotificationStorage.getVapidPublicKeyString();
  }

  private void sendCloseAllNotifications(String username) {
    int sentCount = sendNotification(RANDOM.nextLong(), PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION, username);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_ALL_DELETED, username, null);
    }
  }

  private void sendCloseNotification(Long webNotificationId) {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      LOG.warn("Can't send notification closing event since notification '{}' wasn't found", webNotificationId);
      return;
    }
    int sentCount = sendNotification(notification, PWA_NOTIFICATION_CLOSE_UI_ACTION);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_DELETED, webNotificationId, null);
    }
  }

  private void sendCreateNotification(Long webNotificationId) {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      LOG.warn("Can't send notification creation event since notification '{}' wasn't found", webNotificationId);
      return;
    }
    int sentCount = sendNotification(notification, PWA_NOTIFICATION_OPEN_UI_ACTION);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_CREATED, webNotificationId, null);
    }
  }

  private int sendNotification(NotificationInfo notification, String action) {
    if (notification == null) {
      LOG.warn("Can't send notification action {} since notification is null", action);
      return 0;
    }
    String notificationId = notification.getId();
    String username = notification.getTo();
    if (username != null) {
      return sendNotification(Long.parseLong(notificationId), action, username);
    } else if (notification.getSendToUserIds() != null) {
      return notification.getSendToUserIds()
                         .stream()
                         .map(user -> sendNotification(Long.parseLong(notificationId), action, username))
                         .reduce(0, Integer::sum);
    } else {
      return 0;
    }
  }

  private int sendNotification(long notificationId, String action, String username) {
    List<UserPushSubscription> subscriptions = pwaSubscriptionService.getSubscriptions(username);
    return subscriptions.stream()
                        .map(subscription -> {
                          try {
                            sendPushMessage(subscription, (notificationId + ":" + action).getBytes());
                            return 1;
                          } catch (Exception e) {
                            LOG.warn("Error while sending push notification {} to user {}. Ignore reattempting and continue processing messages queue.",
                                     notificationId,
                                     username,
                                     e);
                            return 0;
                          }
                        })
                        .reduce(0, Integer::sum);
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
