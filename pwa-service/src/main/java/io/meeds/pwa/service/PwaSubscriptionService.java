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

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.DeviceNotificationSetting;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.storage.PwaSubscriptionStorage;

@Service
public class PwaSubscriptionService {

  public static final String     PWA_INSTALLED           = "pwa.installed";

  /**
   * A notification kind is a short technical key (e.g. "chat").
   */
  private static final Pattern   NOTIFICATION_KIND_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,50}");

  /**
   * One day: a deferred popup delayed further stops being a notification.
   */
  private static final int       MAX_DELAY_MINUTES        = 1440;

  public static final String     PWA_UNINSTALLED = "pwa.uninstalled";

  private static final Log       LOG             = ExoLogger.getLogger(PwaSubscriptionService.class);

  @Autowired
  private PwaSubscriptionStorage pwaSubscriptionStorage;

  @Autowired
  private ListenerService        listenerService;

  public List<UserPushSubscription> getSubscriptions(String username) {
    return pwaSubscriptionStorage.get(username);
  }

  public UserPushSubscription getSubscription(String username, String id) {
    return getSubscriptions(username).stream()
                                     .filter(s -> StringUtils.equals(s.getId(), id))
                                     .findFirst()
                                     .orElse(null);
  }

  public void createSubscription(UserPushSubscription subscription,
                                 String username) {
    // per-device settings are managed through saveNotificationSetting only,
    // never taken from the client subscribe body
    subscription.setNotificationSettings(null);
    List<UserPushSubscription> subscriptions = pwaSubscriptionStorage.get(username);
    String endpoint = subscription.getEndpoint();
    UserPushSubscription existingSubscription = subscriptions.stream()
                                                             .filter(s -> StringUtils.equals(s.getEndpoint(), endpoint))
                                                             .findFirst()
                                                             .orElse(null);
    if (existingSubscription == null) {
      LOG.info("Create new subscription with id {} for user {} and endpoint {}",
               subscription.getId(),
               username,
               getSubscriptionDomain(endpoint));
      pwaSubscriptionStorage.create(subscription, username);
      listenerService.broadcast(PWA_INSTALLED, username, subscription);
    } else if (!StringUtils.equals(existingSubscription.getPushDeviceSecret(), subscription.getPushDeviceSecret())
               || !StringUtils.equals(existingSubscription.getId(), subscription.getId())) {
      LOG.info("Update subscription with id {} for user {} and endpoint {}",
               subscription.getId(),
               username,
               getSubscriptionDomain(endpoint));
      // client-sent settings were discarded at entry: the stored ones survive
      subscription.setNotificationSettings(existingSubscription.getNotificationSettings());
      pwaSubscriptionStorage.delete(existingSubscription.getId(), username);
      pwaSubscriptionStorage.create(subscription, username);
    } else {
      LOG.debug("Subscription for endpoint {} already exists for user {}", getSubscriptionDomain(endpoint), username);
    }
  }

  /**
   * @param username subscription owner
   * @param subscriptionId the device subscription id
   * @param notificationKind the direct-notification kind (e.g. "chat")
   * @return the device's stored setting for that kind, or null when the
   *         device follows the defaults (enabled, caller-chosen delay)
   * @throws ObjectNotFoundException when no such subscription exists
   */
  public DeviceNotificationSetting getNotificationSetting(String username,
                                                          String subscriptionId,
                                                          String notificationKind) throws ObjectNotFoundException {
    UserPushSubscription subscription = getSubscription(username, subscriptionId);
    if (subscription == null) {
      throw new ObjectNotFoundException(String.format("Subscription %s of user %s not found", subscriptionId, username));
    }
    return subscription.getNotificationSetting(notificationKind);
  }

  /**
   * Saves one direct-notification kind's setting on one device subscription.
   *
   * @param username subscription owner
   * @param subscriptionId the device subscription id
   * @param notificationKind the direct-notification kind (e.g. "chat")
   * @param setting the {enabled, delayMinutes} pair to store
   * @throws ObjectNotFoundException when no such subscription exists
   */
  public void saveNotificationSetting(String username,
                                      String subscriptionId,
                                      String notificationKind,
                                      DeviceNotificationSetting setting) throws ObjectNotFoundException {
    if (setting == null
        || StringUtils.isBlank(notificationKind)
        || !NOTIFICATION_KIND_PATTERN.matcher(notificationKind).matches()) {
      throw new IllegalArgumentException("pwa.notificationSetting.invalidKind");
    }
    if (setting.getDelayMinutes() != null
        && (setting.getDelayMinutes() < 1 || setting.getDelayMinutes() > MAX_DELAY_MINUTES)) {
      throw new IllegalArgumentException("pwa.notificationSetting.invalidDelay");
    }
    UserPushSubscription subscription = getSubscription(username, subscriptionId);
    if (subscription == null) {
      throw new ObjectNotFoundException(String.format("Subscription %s of user %s not found", subscriptionId, username));
    }
    subscription.setNotificationSetting(notificationKind, setting);
    pwaSubscriptionStorage.create(subscription, username);
  }

  public void deleteSubscription(String id, String username) {
    deleteSubscription(id, username, true);
  }

  public void deleteSubscription(String id, String username, boolean userAction) {
    UserPushSubscription subscription = pwaSubscriptionStorage.delete(id, username);
    if (userAction && subscription != null) {
      listenerService.broadcast(PWA_UNINSTALLED, username, subscription);
    }
  }

  public void deleteAllSubscriptions(String username) {
    List<UserPushSubscription> subscriptions = getSubscriptions(username);
    subscriptions.forEach(s -> deleteSubscription(s.getId(), username));
  }

  private String getSubscriptionDomain(String endpoint) {
    return endpoint.substring(0, endpoint.indexOf("/", 15));
  }

}
