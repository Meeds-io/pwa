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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.Constants;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationAction;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.plugin.DefaultPwaNotificationPlugin;
import io.meeds.pwa.plugin.PwaBadgePlugin;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import io.meeds.pwa.storage.PwaNotificationStorage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
@Slf4j
public class PwaNotificationService {

  public static final String           PROOF_PARAM                                   = "proof";

  public static final String           TIMESTAMP_PARAM                               = "timestamp";

  public static final String           SUBSCRIPTION_ID_PARAM                         = "subscriptionId";

  public static final String           PUSH_TOKEN_PARAM                              = "token";

  public static final String           PWA_NOTIFICATION_CREATED                      = "pwa.notification.created";

  public static final String           PWA_NOTIFICATION_RECEIVED                     = "pwa.notification.received";

  public static final String           PWA_NOTIFICATION_OPEN_UI_ACTION               = "open";

  public static final String           PWA_NOTIFICATION_MARK_READ_USER_ACTION        = "markRead";

  public static final String           PWA_NOTIFICATION_MARK_READ_ACTION_LABEL       = "pwa.notification.action.markAsRead";

  public static final String           PWA_NOTIFICATION_PUSH_DELAY_TIME              = "pwa.notification.pushDelay";

  public static final String           PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME = "pwa.notification.pushEffectiveReceivedAt";

  public static final String           PWA_NOTIFICATION_PUSH_RECEIVED_TIME           = "pwa.notification.pushReceivedAt";

  public static final String           PWA_NOTIFICATION_PUSH_SENT_TIME               = "pwa.notification.pushSentAt";

  public static final String           EVENT_NOTIFICATION_SENT                       = "pwa.notificationSent";

  public static final String           EVENT_NOTIFICATION_RESPONSE_ERROR             = "pwa.notificationResponseError";

  public static final String           EVENT_NOTIFICATION_SENDING_ERROR              = "pwa.notificationSendingError";

  public static final String           EVENT_OUTDATED_SUBSCRIPTION                   = "pwa.outdatedSubscription";

  public static final String           EVENT_ERROR_PARAM_NAME                        = "error";

  public static final String           EVENT_SUBSCRIPTION_PARAM_NAME                 = "subscription";

  public static final String           EVENT_HTTP_RESPONSE_PARAM_NAME                = "httpResponse";

  public static final String           EVENT_NOTIFICATION_ID_PARAM_NAME              = "notificationId";

  public static final String           EVENT_ACTION_PARAM_NAME                       = "action";

  public static final String           EVENT_NOTIFICATION_TYPE_PARAM_NAME            = "type";

  public static final String           WEB_NOTIFICATION                              = "WEB_NOTIFICATION";

  public static final String           EVENT_USERNAME_PARAM_NAME                     = "username";

  public static final String           EVENT_DURATION_PARAM_NAME                     = "duration";

  public static final String           EVENT_NOTIFICATION_TOKEN_PARAM_NAME           = PUSH_TOKEN_PARAM;

  public static final String           EVENT_SUBSCRIPTION_ID_PARAM_NAME              = SUBSCRIPTION_ID_PARAM;

  private static final String          MSG_NOTIFICATION_ACCESS_DENIED                = "Notification with id %s access denied";

  private static final String          MSG_NOTIFICATION_NOT_FOUND                    = "Notification with id %s doesn't exists";

  private static final String          PUSH_AUTH_SCHEME                              = "PWA-Notification";

  private static final String          HMAC_ALGORITHM                                = "HmacSHA256";

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

  @Autowired
  private PwaNotificationTokenService  pwaNotificationTokenService;

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

  @Value("${pwa.notifications.push.token.ttl.seconds:28800}") // 8hours
  private int                          pushTokenTtlSeconds;

  // 1 hour
  @Value("${pwa.notifications.push.token.ttl.excessiveDelayThreshold:3600}")
  private int                          pushNotificationExcessiveDelayThreshold;

  @Autowired
  private List<PwaNotificationPlugin>  plugins;

  @Autowired(required = false)
  private List<PwaBadgePlugin>         badgePlugins                                  = Collections.emptyList();

  private ScheduledExecutorService     executorService;

  @PostConstruct
  public void init() {
    ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("PWA-Push-Notification-%d")
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
      throw new ObjectNotFoundException(String.format(MSG_NOTIFICATION_NOT_FOUND, webNotificationId));
    } else if (!StringUtils.equals(notification.getTo(), username)) {
      throw new IllegalAccessException(String.format(MSG_NOTIFICATION_ACCESS_DENIED, webNotificationId));
    }
    String pluginId = notification.getKey().getId();
    PwaNotificationPlugin notificationPlugin = plugins.stream()
                                                      .filter(p -> StringUtils.equals(p.getId(), pluginId))
                                                      .findFirst()
                                                      .orElse(defaultPwaNotificationPlugin);
    LocaleConfig localeConfig = getLocaleConfig(username);
    PwaNotificationMessage notificationMessage = notificationPlugin.process(notification, localeConfig);
    setDefaultNotificationMessageProperties(notificationMessage, notification.getId(), localeConfig);
    return notificationMessage;
  }

  public PwaNotificationMessage getNotificationFromPush(long webNotificationId,
                                                        String authorizationHeader,
                                                        String username) throws ObjectNotFoundException,
                                                                         IllegalAccessException {
    if (StringUtils.isBlank(username)) {
      username = validatePushNotificationAccess(webNotificationId, authorizationHeader, true);
    }
    return getNotification(webNotificationId, username);
  }

  public void updateNotificationFromPush(long webNotificationId,
                                         String action,
                                         String authorizationHeader,
                                         String username) throws ObjectNotFoundException,
                                                          IllegalAccessException {
    if (StringUtils.isBlank(username)) {
      username = validatePushNotificationAccess(webNotificationId, authorizationHeader, true);
    }
    updateNotification(webNotificationId, action, username);
  }

  public void reportPushDeliveryDelay(long webNotificationId,
                                      String authorizationHeader,
                                      String username,
                                      long sentAt,
                                      long receivedAt) throws IllegalAccessException, ObjectNotFoundException {
    if (StringUtils.isBlank(username)) {
      username = validatePushNotificationAccess(webNotificationId, authorizationHeader, false);
    }
    String subscriptionId = parsePushAuthorizationHeader(authorizationHeader).get(SUBSCRIPTION_ID_PARAM);
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      throw new ObjectNotFoundException(String.format(MSG_NOTIFICATION_NOT_FOUND, webNotificationId));
    } else if (!StringUtils.equals(notification.getTo(), username)) {
      throw new IllegalAccessException(String.format(MSG_NOTIFICATION_ACCESS_DENIED, webNotificationId));
    }
    // @formatter:off
    // Both generated in server side, thus it's compatible rather than using the Client time in MS
    long computedDelayMs = System.currentTimeMillis() - sentAt;
    // @formatter:on
    if ((computedDelayMs / 1000) > pushNotificationExcessiveDelayThreshold) {
      pwaNotificationStorage.recordExcessivePushDeliveryDelay(username, subscriptionId, Math.max(0, computedDelayMs));
    }
    Map<String, String> ownerParameters = notification.getOwnerParameter() == null ? new HashMap<>() :
                                                                                   new HashMap<>(notification.getOwnerParameter());
    ownerParameters.put(PWA_NOTIFICATION_PUSH_SENT_TIME, String.valueOf(sentAt));
    ownerParameters.put(PWA_NOTIFICATION_PUSH_RECEIVED_TIME, String.valueOf(receivedAt));
    ownerParameters.put(PWA_NOTIFICATION_PUSH_EFFECTIVE_RECEIVED_TIME, String.valueOf(System.currentTimeMillis()));
    ownerParameters.put(PWA_NOTIFICATION_PUSH_DELAY_TIME, String.valueOf(System.currentTimeMillis() - sentAt));
    webNotificationService.updateNotificationParameters(notification.getId(), ownerParameters);

    UserPushSubscription subscription = pwaSubscriptionService.getSubscription(username, subscriptionId);
    notification.setOwnerParameter(ownerParameters);
    listenerService.broadcast(PWA_NOTIFICATION_RECEIVED,
                              subscription,
                              notification);
  }

  public Map<String, Object> getPushDeliveryDelayStatus(String username, String subscriptionId) {
    return pwaNotificationStorage.getPushDeliveryDelayStatus(username, subscriptionId, TimeUnit.HOURS.toMillis(1));
  }

  public void resetPushDeliveryDelay(String username, String subscriptionId) {
    pwaNotificationStorage.resetPushDeliveryDelay(username, subscriptionId);
  }

  public Map<String, Object> getBadge(String username) {
    Map<String, Integer> badges = new HashMap<>();
    int total = 0;
    for (PwaBadgePlugin badgePlugin : badgePlugins) {
      try {
        if (badgePlugin.isEnabled(username)) {
          int count = Math.max(0, badgePlugin.getBadge(username));
          badges.put(badgePlugin.getId(), count);
          total += count;
        }
      } catch (Exception e) {
        log.warn("Error computing PWA badge for plugin {} and user {}. Continue computing other type of badges.",
                 badgePlugin.getId(),
                 username,
                 e);
      }
    }
    Map<String, Object> badge = new HashMap<>();
    badge.put("badges", badges);
    badge.put("total", total);
    return badge;
  }

  public void updateNotification(long webNotificationId, String action, String username) throws ObjectNotFoundException,
                                                                                         IllegalAccessException {
    NotificationInfo notification = webNotificationService.getNotificationInfo(String.valueOf(webNotificationId));
    if (notification == null) {
      throw new ObjectNotFoundException(String.format(MSG_NOTIFICATION_NOT_FOUND, webNotificationId));
    } else if (!StringUtils.equals(notification.getTo(), username)) {
      throw new IllegalAccessException(String.format(MSG_NOTIFICATION_ACCESS_DENIED, webNotificationId));
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
   * Send a Push Notification to display to user device(s)
   *
   * @param params
   */
  public ScheduledFuture<?> create(Map<String, Object> params) { // NOSONAR
    if (pwaManifestService.isPwaEnabled()) {
      return executorService.schedule(() -> this.sendNotification(params), 1, TimeUnit.SECONDS);
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
      log.warn("Can't send notification action {} since notification is null", action);
      return 0;
    }
    String notificationId = notification.getId();
    String username = notification.getTo();
    HashMap<String, Object> params = new HashMap<>();
    params.put(EVENT_NOTIFICATION_ID_PARAM_NAME, Long.parseLong(notificationId));
    params.put(EVENT_ACTION_PARAM_NAME, action);
    params.put(EVENT_NOTIFICATION_TYPE_PARAM_NAME, WEB_NOTIFICATION);
    if (username != null) {
      params.put(EVENT_USERNAME_PARAM_NAME, username);
      return sendNotification(params);
    } else if (notification.getSendToUserIds() != null) {
      return notification.getSendToUserIds()
                         .stream()
                         .map(user -> {
                           params.put(EVENT_USERNAME_PARAM_NAME, user);
                           return sendNotification(params);
                         })
                         .reduce(0, Integer::sum);
    } else {
      return 0;
    }
  }

  private int sendNotification(Map<String, Object> params) { // NOSONAR
    String userName = params.get(EVENT_USERNAME_PARAM_NAME).toString();
    List<UserPushSubscription> subscriptions = pwaSubscriptionService.getSubscriptions(userName);
    return subscriptions.stream()
                        .map(subscription -> {
                          long start = System.currentTimeMillis();
                          try {
                            String notificationType =
                                                    StringUtils.isNotBlank((String) params.get(EVENT_NOTIFICATION_TYPE_PARAM_NAME)) ?
                                                                                                                                    (String) params.get(EVENT_NOTIFICATION_TYPE_PARAM_NAME) :
                                                                                                                                    WEB_NOTIFICATION;
                            String payload = "%s:%s:%s".formatted(notificationType,
                                                                  params.get(EVENT_NOTIFICATION_ID_PARAM_NAME),
                                                                  params.get(EVENT_ACTION_PARAM_NAME));
                            if (StringUtils.equals(notificationType, WEB_NOTIFICATION)) {
                              long sentAt = System.currentTimeMillis();
                              String pushToken = generatePushNotificationAccessToken(params, subscription);
                              if (StringUtils.isNotBlank(pushToken)) {
                                payload += ":%s:%s:%s".formatted(pushToken, subscription.getId(), sentAt);
                              } else {
                                payload += ":::%s".formatted(sentAt);
                              }
                            }
                            HttpResponse httpResponse = sendPushMessage(subscription, payload.getBytes());
                            StatusLine status = httpResponse.getStatusLine();
                            if (status.getStatusCode() == 410) {
                              // Outdated subscription
                              try {
                                pwaSubscriptionService.deleteSubscription(subscription.getId(), userName, false);
                              } finally {
                                broadcastEvent(EVENT_OUTDATED_SUBSCRIPTION,
                                               params,
                                               subscription,
                                               httpResponse,
                                               start,
                                               null);
                              }
                            } else if (status.getStatusCode() < 200 || status.getStatusCode() > 299) {
                              broadcastEvent(EVENT_NOTIFICATION_RESPONSE_ERROR,
                                             params,
                                             subscription,
                                             httpResponse,
                                             start,
                                             null);
                            } else {
                              // Other push notifications managed by specific
                              // application should create their own statistics
                              if (params.get(EVENT_NOTIFICATION_TYPE_PARAM_NAME).equals(WEB_NOTIFICATION)) {
                                broadcastEvent(EVENT_NOTIFICATION_SENT,
                                               params,
                                               subscription,
                                               httpResponse,
                                               start,
                                               null);
                              }
                              return 1;
                            }
                          } catch (Exception e) {
                            log.warn("Error while sending push notification {} to user {}. Ignore reattempting and continue processing messages queue.",
                                     params.get(EVENT_NOTIFICATION_ID_PARAM_NAME),
                                     userName,
                                     e);

                            broadcastEvent(EVENT_NOTIFICATION_SENDING_ERROR,
                                           params,
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
    Notification notification = new Notification(sub.getEndpoint(),
                                                 sub.userPublicKey(),
                                                 sub.authAsBytes(),
                                                 payload,
                                                 pushTokenTtlSeconds);
    // Send the notification
    return pushService.send(notification);
  }

  private String generatePushNotificationAccessToken(Map<String, Object> params, UserPushSubscription subscription) {
    if (subscription == null || StringUtils.isBlank(subscription.getId())
        || StringUtils.isBlank(subscription.getPushDeviceSecret())) {
      return null;
    }
    String username = String.valueOf(params.get(EVENT_USERNAME_PARAM_NAME));
    long notificationId = Long.parseLong(String.valueOf(params.get(EVENT_NOTIFICATION_ID_PARAM_NAME)));
    return pwaNotificationTokenService.createToken(username, notificationId, subscription.getId());
  }

  private String validatePushNotificationAccess(long notificationId,
                                                String authorizationHeader,
                                                boolean consume) throws IllegalAccessException {
    Map<String, String> parameters = parsePushAuthorizationHeader(authorizationHeader);
    String token = parameters.get(PUSH_TOKEN_PARAM);
    String subscriptionId = parameters.get(SUBSCRIPTION_ID_PARAM);
    String timestamp = parameters.get(TIMESTAMP_PARAM);
    String proof = parameters.get(PROOF_PARAM);
    if (StringUtils.isAnyBlank(token, subscriptionId, timestamp, proof)) {
      throw new IllegalAccessException("Missing push authorization parameters");
    }
    long timestampValue;
    try {
      timestampValue = Long.parseLong(timestamp);
    } catch (NumberFormatException e) {
      throw new IllegalAccessException("Invalid push authorization timestamp");
    }
    long now = System.currentTimeMillis();
    if (Math.abs(now - timestampValue) > TimeUnit.SECONDS.toMillis(pushTokenTtlSeconds)) {
      throw new IllegalAccessException("Expired push authorization proof");
    }
    String username = pwaNotificationTokenService.validateToken(token, notificationId, subscriptionId);
    if (StringUtils.isBlank(username)) {
      throw new IllegalAccessException("Invalid push notification token");
    }
    UserPushSubscription subscription = pwaSubscriptionService.getSubscription(username, subscriptionId);
    if (subscription == null || StringUtils.isBlank(subscription.getPushDeviceSecret())) {
      throw new IllegalAccessException("Unknown push subscription");
    }
    String expectedProof = computeHmac(subscription.getPushDeviceSecret(),
                                       notificationId + ":" + token + ":" + subscriptionId + ":" + timestamp);
    if (!MessageDigest.isEqual(expectedProof.getBytes(StandardCharsets.UTF_8), proof.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalAccessException("Invalid push authorization proof");
    }
    if (consume) {
      String consumedUsername = pwaNotificationTokenService.consumeToken(token, notificationId, subscriptionId);
      if (!StringUtils.equals(username, consumedUsername)) {
        throw new IllegalAccessException("Push notification token already consumed");
      }
    }
    return username;
  }

  private Map<String, String> parsePushAuthorizationHeader(String authorizationHeader) throws IllegalAccessException {
    if (StringUtils.isBlank(authorizationHeader) || !StringUtils.startsWith(authorizationHeader, PUSH_AUTH_SCHEME + " ")) {
      throw new IllegalAccessException("Missing push authorization header");
    }
    Map<String, String> parameters = new HashMap<>();
    String[] parts = StringUtils.substringAfter(authorizationHeader, PUSH_AUTH_SCHEME + " ").split(",");
    for (String part : parts) {
      String key = StringUtils.trim(StringUtils.substringBefore(part, "="));
      String value = StringUtils.trim(StringUtils.substringAfter(part, "="));
      value = StringUtils.removeStart(value, "\"");
      value = StringUtils.removeEnd(value, "\"");
      if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
        parameters.put(key, value);
      }
    }
    return parameters;
  }

  private String computeHmac(String secret, String value) throws IllegalAccessException {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), HMAC_ALGORITHM));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalAccessException("Unable to compute push authorization proof");
    }
  }

  public void setDefaultNotificationMessageProperties(PwaNotificationMessage notificationMessage,
                                                      String notificationId,
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
      notificationMessage.setTag(notificationId);
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
      log.warn("Error retrieving user {} language, use default language {}", username, defaultLocaleConfig.getLanguage());
      return defaultLocaleConfig;
    }
  }

  private void broadcastEvent(String eventName, // NOSONAR
                              Map<String, Object> params,
                              UserPushSubscription subscription,
                              HttpResponse httpResponse,
                              long start,
                              String errorMessage) {
    params.put(EVENT_SUBSCRIPTION_PARAM_NAME, subscription);
    params.put(EVENT_ERROR_PARAM_NAME, errorMessage);
    params.put(EVENT_DURATION_PARAM_NAME, (System.currentTimeMillis() - start));
    params.put(EVENT_HTTP_RESPONSE_PARAM_NAME, httpResponse);
    listenerService.broadcast(eventName, params.get(EVENT_USERNAME_PARAM_NAME), params);
  }

}
