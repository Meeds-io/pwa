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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
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

  public static final String           PWA_NOTIFICATION_DELETED                = "pwa.notification.deleted";

  public static final String           PWA_NOTIFICATION_ALL_DELETED            = "pwa.notification.allDeleted";

  public static final String           PWA_NOTIFICATION_OPEN_UI_ACTION         = "open";

  public static final String           PWA_NOTIFICATION_CLOSE_UI_ACTION        = "close";

  public static final String           PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION    = "closeAll";

  public static final String           PWA_NOTIFICATION_MARK_READ_USER_ACTION  = "markRead";

  public static final String           PWA_NOTIFICATION_MARK_READ_ACTION_LABEL = "pwa.notification.action.markAsRead";

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
   * Delete a previously displayed Push Notification to End user due if not
   * dismissed yet
   * 
   * @param webNotificationId
   * @return
   */
  public ScheduledFuture<?> delete(long webNotificationId) { // NOSONAR
    if (pwaManifestService.isPwaEnabled()) {
      return executorService.schedule(() -> this.sendCloseNotification(webNotificationId), 1, TimeUnit.SECONDS);
    } else {
      return null;
    }
  }

  /**
   * Delete all previously displayed push notifications to user's device(s)
   * 
   * @param username
   */
  public ScheduledFuture<?> deleteAll(String username) { // NOSONAR
    if (pwaManifestService.isPwaEnabled()) {
      return executorService.schedule(() -> this.sendCloseAllNotifications(username), 1, TimeUnit.SECONDS);
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

  private int sendCloseAllNotifications(String username) {
    int sentCount = sendNotification(RANDOM.nextLong(), PWA_NOTIFICATION_CLOSE_ALL_UI_ACTION, username);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_ALL_DELETED, username, null);
    }
    return sentCount;
  }

  private int sendCloseNotification(Long webNotificationId) {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    int sentCount = sendNotification(notification, PWA_NOTIFICATION_CLOSE_UI_ACTION);
    if (sentCount > 0) {
      listenerService.broadcast(PWA_NOTIFICATION_DELETED, webNotificationId, null);
    }
    return sentCount;
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
                          try {
                            String payload = notificationId + ":" + action;
                            String endpoint = subscription.getEndpoint();
                            HttpResponse httpResponse = sendPushMessage(subscription, payload.getBytes());
                            StatusLine status = httpResponse.getStatusLine();
                            if (status.getStatusCode() == 410) { // Outdated
                                                                 // subscription
                              LOG.info("Subscription of user '{}' on device with id '{}' using url '{}' is outdated, delete it",
                                       username,
                                       subscription.getId(),
                                       getSubscriptionDomain(endpoint));
                              pwaSubscriptionService.deleteSubscription(subscription.getId(), username);
                            } else if (status.getStatusCode() < 200 || status.getStatusCode() > 299) {
                              InputStream inputStream = httpResponse.getEntity() == null ? null :
                                                                                         httpResponse.getEntity().getContent();
                              try {
                                LOG.warn("Notification with id '{}' for user '{}' with action '{}' on device with id '{}' using url '{}' not sent with error response code '{} {}' and message '{}'",
                                         notificationId,
                                         username,
                                         action,
                                         subscription.getId(),
                                         getSubscriptionDomain(endpoint),
                                         status.getStatusCode(),
                                         status.getReasonPhrase(),
                                         inputStream == null ? null : IOUtils.toString(inputStream, StandardCharsets.UTF_8));
                              } finally {
                                if (inputStream != null) {
                                  inputStream.close();
                                }
                              }
                            } else {
                              LOG.info("Notification with id '{}' for user '{}' with action '{}' on device with id '{}' using url '{}' sent successfully",
                                       notificationId,
                                       username,
                                       action,
                                       subscription.getId(),
                                       getSubscriptionDomain(endpoint));
                              return 1;
                            }
                          } catch (Exception e) {
                            LOG.warn("Error while sending push notification {} to user {}. Ignore reattempting and continue processing messages queue.",
                                     notificationId,
                                     username,
                                     e);
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

  private String getSubscriptionDomain(String endpoint) {
    return endpoint.substring(0, endpoint.indexOf("/", 15));
  }

}
