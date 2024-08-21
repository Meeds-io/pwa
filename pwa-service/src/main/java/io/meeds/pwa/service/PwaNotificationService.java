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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.Constants;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationAction;
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

  public static final String           PWA_NOTIFICATION_CREATED                = "pwa.notification.created";

  public static final String           PWA_NOTIFICATION_OPEN_UI_ACTION         = "open";

  public static final String           PWA_NOTIFICATION_MARK_READ_USER_ACTION  = "markRead";

  public static final String           PWA_NOTIFICATION_MARK_READ_ACTION_LABEL = "pwa.notification.action.markAsRead";

  public static final String           EVENT_NOTIFICATION_SENT                 = "pwa.notificationSent";

  public static final String           EVENT_NOTIFICATION_RESPONSE_ERROR       = "pwa.notificationResponseError";

  public static final String           EVENT_NOTIFICATION_SENDING_ERROR        = "pwa.notificationSendingError";

  public static final String           EVENT_OUTDATED_SUBSCRIPTION             = "pwa.outdatedSubscription";

  public static final String           EVENT_ERROR_PARAM_NAME                  = "error";

  public static final String           EVENT_SUBSCRIPTION_PARAM_NAME           = "subscription";

  public static final String           EVENT_HTTP_RESPONSE_PARAM_NAME          = "httpResponse";

  public static final String           EVENT_NOTIFICATION_ID_PARAM_NAME        = "notificationId";

  public static final String           EVENT_ACTION_PARAM_NAME                 = "action";

  public static final String           EVENT_DURATION_PARAM_NAME               = "duration";

  public static final Random           RANDOM                                  = new Random();

  private static final Log             LOG                                     =
                                           ExoLogger.getLogger(PwaNotificationService.class);

  @Autowired
  private PwaManifestService           pwaManifestService;

  @Autowired
  private PwaSubscriptionService       pwaSubscriptionService;

  @Autowired
  private PwaNotificationStorage       pwaNotificationStorage;

  @Autowired
  private WebNotificationService       webNotificationService;

  @Autowired
  private ListenerService              listenerService;

  @Autowired
  private OrganizationService          organizationService;

  @Autowired
  private LocaleConfigService          localeConfigService;

  @Autowired
  private ResourceBundleService        resourceBundleService;

  @Autowired
  private DefaultPwaNotificationPlugin defaultPwaNotificationPlugin;

  @Autowired
  private PushService                  pushService;

  @Value("${pwa.notifications.enabled:true}")
  private boolean                      enabled;

  @Value("${pwa.notifications.pool.size:5}")
  private int                          poolSize;

  @Value("${pwa.notifications.maxBodyLength:75}")
  private int                          maxBodyLength;

  @Value("${pwa.notifications.requireInteraction:true}")
  private boolean                      requireInteraction;

  @Value("${pwa.notifications.renotify:true}")
  private boolean                      renotify;

  @Value("${pwa.notifications.silent:false}")
  private boolean                      silent;

  @Autowired
  private List<PwaNotificationPlugin>  plugins;

  private ScheduledExecutorService     executorService;

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
    String pluginId = notification.getKey().getId();
    PwaNotificationPlugin notificationPlugin = plugins.stream()
                                                      .filter(p -> StringUtils.equals(p.getId(), pluginId))
                                                      .findFirst()
                                                      .orElse(defaultPwaNotificationPlugin);
    LocaleConfig localeConfig = getLocaleConfig(username);
    PwaNotificationMessage notificationMessage = notificationPlugin.process(notification, localeConfig);
    setDefaultNotificationMessageProperties(notificationMessage, notification, localeConfig);
    return notificationMessage;
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
    } else {
      String pluginId = notification.getKey().getId();
      PwaNotificationPlugin notificationPlugin = plugins.stream()
                                                        .filter(p -> StringUtils.equals(p.getId(), pluginId))
                                                        .findFirst()
                                                        .orElse(defaultPwaNotificationPlugin);
      notificationPlugin.handleAction(notification, action, username);
    }
  }

  /**
   * Send a Push Notification to display to user device(s)
   * 
   * @param webNotificationId
   */
  public ScheduledFuture<?> create(long webNotificationId) { // NOSONAR
    if (pwaManifestService.isPwaEnabled()) {
      return executorService.schedule(() -> this.sendCreateNotification(webNotificationId), 1, TimeUnit.SECONDS);
    } else {
      return null;
    }
  }

  /**
   * @return VAPID Public Key encoded using Base64url
   */
  public String getVapidPublicKeyString() {
    return pwaNotificationStorage.getVapidPublicKeyString();
  }

  private int sendCreateNotification(Long webNotificationId) {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    int sentCount = sendNotification(notification, PWA_NOTIFICATION_OPEN_UI_ACTION);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_CREATED, webNotificationId, null);
    }
    return sentCount;
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
                          long start = System.currentTimeMillis();
                          try {
                            String payload = notificationId + ":" + action;
                            HttpResponse httpResponse = sendPushMessage(subscription, payload.getBytes());
                            StatusLine status = httpResponse.getStatusLine();
                            if (status.getStatusCode() == 410) {
                              // Outdated subscription
                              try {
                                pwaSubscriptionService.deleteSubscription(subscription.getId(), username, false);
                              } finally {
                                broadcastEvent(EVENT_OUTDATED_SUBSCRIPTION,
                                               notificationId,
                                               action,
                                               username,
                                               subscription,
                                               httpResponse,
                                               start,
                                               null);
                              }
                            } else if (status.getStatusCode() < 200 || status.getStatusCode() > 299) {
                              broadcastEvent(EVENT_NOTIFICATION_RESPONSE_ERROR,
                                             notificationId,
                                             action,
                                             username,
                                             subscription,
                                             httpResponse,
                                             start,
                                             null);
                            } else {
                              broadcastEvent(EVENT_NOTIFICATION_SENT,
                                             notificationId,
                                             action,
                                             username,
                                             subscription,
                                             httpResponse,
                                             start,
                                             null);
                              return 1;
                            }
                          } catch (Exception e) {
                            LOG.warn("Error while sending push notification {} to user {}. Ignore reattempting and continue processing messages queue.",
                                     notificationId,
                                     username,
                                     e);

                            broadcastEvent(EVENT_NOTIFICATION_SENDING_ERROR,
                                           notificationId,
                                           action,
                                           username,
                                           subscription,
                                           null,
                                           start,
                                           e.getMessage());
                          }
                          return 0;
                        })
                        .reduce(0, Integer::sum);
  }

  private HttpResponse sendPushMessage(UserPushSubscription sub, byte[] payload) throws Exception { // NOSONAR
    Notification notification = new Notification(
                                                 sub.getEndpoint(),
                                                 sub.userPublicKey(),
                                                 sub.authAsBytes(),
                                                 payload);
    // Send the notification
    return pushService.send(notification);
  }

  private void setDefaultNotificationMessageProperties(PwaNotificationMessage notificationMessage,
                                                       NotificationInfo notification,
                                                       LocaleConfig localeConfig) {
    List<PwaNotificationAction> notificationActions = notificationMessage.getActions();
    if (CollectionUtils.isEmpty(notificationMessage.getActions())
        || notificationActions.stream()
                              .noneMatch(a -> StringUtils.equals(a.getAction(), PWA_NOTIFICATION_MARK_READ_USER_ACTION))) {
      notificationActions = notificationActions == null ? new ArrayList<>() : new ArrayList<>(notificationActions);
      notificationActions.add(new PwaNotificationAction(resourceBundleService.getSharedString(PWA_NOTIFICATION_MARK_READ_ACTION_LABEL,
                                                                                              localeConfig.getLocale()),
                                                        PWA_NOTIFICATION_MARK_READ_USER_ACTION));
      notificationMessage.setActions(notificationActions);
    }
    notificationMessage.setRequireInteraction(requireInteraction);
    notificationMessage.setRenotify(renotify);
    notificationMessage.setSilent(silent);
    notificationMessage.setLang(localeConfig.getLanguage());
    notificationMessage.setDir(localeConfig.getOrientation() == null || localeConfig.getOrientation().isLT() ? "ltr" : "rtl");
    if (StringUtils.isBlank(notificationMessage.getTag())) {
      notificationMessage.setTag(notification.getId());
    }
    if (StringUtils.length(notificationMessage.getBody()) > maxBodyLength) {
      notificationMessage.setBody(notificationMessage.getBody().substring(0, maxBodyLength) + "...");
    }
    if (StringUtils.isBlank(notificationMessage.getUrl())) {
      notificationMessage.setUrl("/");
    }
  }

  public LocaleConfig getLocaleConfig(String username) {
    try {
      UserProfile userProfile = organizationService.getUserProfileHandler().findUserProfileByName(username);
      String language = userProfile == null ? null : userProfile.getAttribute(Constants.USER_LANGUAGE);
      return language == null ? localeConfigService.getDefaultLocaleConfig() : localeConfigService.getLocaleConfig(language);
    } catch (Exception e) {
      LocaleConfig defaultLocaleConfig = localeConfigService.getDefaultLocaleConfig();
      LOG.warn("Error retrieving user {} language, use default language {}", username, defaultLocaleConfig.getLanguage());
      return defaultLocaleConfig;
    }
  }

  private void broadcastEvent(String eventName, // NOSONAR
                              long notificationId,
                              String action,
                              String username,
                              UserPushSubscription subscription,
                              HttpResponse httpResponse,
                              long start,
                              String errorMessage) {
    Map<String, Object> params = new HashMap<>();
    params.put(EVENT_SUBSCRIPTION_PARAM_NAME, subscription);
    params.put(EVENT_ERROR_PARAM_NAME, errorMessage);
    params.put(EVENT_ACTION_PARAM_NAME, action);
    params.put(EVENT_DURATION_PARAM_NAME, (System.currentTimeMillis() - start));
    params.put(EVENT_NOTIFICATION_ID_PARAM_NAME, notificationId);
    params.put(EVENT_HTTP_RESPONSE_PARAM_NAME, httpResponse);
    listenerService.broadcast(eventName, username, params);
  }

}
