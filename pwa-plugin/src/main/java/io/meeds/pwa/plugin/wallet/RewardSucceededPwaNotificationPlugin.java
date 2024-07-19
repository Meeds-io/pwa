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
package io.meeds.pwa.plugin.wallet;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;

@Profile("wallet")
@Component
public class RewardSucceededPwaNotificationPlugin implements PwaNotificationPlugin {
  private static final String            REWAR_ADMINISTRATION_URL = "/portal/administration/home/recognition/reward";

  private static final String            TITLE_LABEL_KEY          = "pwa.notification.RewardSuccessNotificationPlugin.title";

  private static final String            BODY_LABEL_KEY           = "pwa.notification.RewardSuccessNotificationPlugin.body";

  private static final DateTimeFormatter DATE_FORMATTER           =
                                                        DateTimeFormatter.ofPattern("d MMM uuuu");

  @Autowired
  private ResourceBundleService          resourceBundleService;

  @Override
  public String getId() {
    return "RewardSuccessNotificationPlugin";
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    Locale locale = localeConfig.getLocale();
    notificationMessage.setTitle(resourceBundleService.getSharedString(TITLE_LABEL_KEY, locale));
    notificationMessage.setBody(resourceBundleService.getSharedString(BODY_LABEL_KEY, locale)
                                                     .replace("{0}", getDateLabel(notification, "rewardStartPeriodDate", locale))
                                                     .replace("{1}", getDateLabel(notification, "rewardEndPeriodDate", locale)));
    notificationMessage.setUrl(REWAR_ADMINISTRATION_URL);
    return notificationMessage;
  }

  private String getDateLabel(NotificationInfo notification, String key, Locale locale) {
    String rewardStartPeriodDate = notification.getValueOwnerParameter(key);
    return formatTime(rewardStartPeriodDate, ZoneOffset.UTC, locale);
  }

  private static String formatTime(Object timeInSeconds, ZoneId zoneId, Locale userLocale) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(String.valueOf(timeInSeconds))),
                                                     zoneId);
    return dateTime.format(DATE_FORMATTER.withLocale(userLocale));
  }

}
