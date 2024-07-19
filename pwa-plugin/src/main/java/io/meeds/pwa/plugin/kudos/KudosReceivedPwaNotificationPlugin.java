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
package io.meeds.pwa.plugin.kudos;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.social.BaseActivityPwaNotificationPlugin;
import io.meeds.social.permlink.plugin.ActivityPermanentLinkPlugin;

@Profile("kudos")
@Component
public class KudosReceivedPwaNotificationPlugin extends BaseActivityPwaNotificationPlugin {

  private static final String   TITLE_LABEL_KEY = "pwa.notification.KudosActivityReceiverNotificationPlugin.title";

  @Override
  public String getId() {
    return "KudosActivityReceiverNotificationPlugin";
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    notificationMessage.setTitle(resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale())
                                                      .replace("{0}",
                                                               getFullName(notification.getValueOwnerParameter("SENDER_ID"))));
    notificationMessage.setBody(htmlToText(notification.getValueOwnerParameter("KUDOS_MESSAGE")));
    String activityId = notification.getValueOwnerParameter(SocialNotificationUtils.ACTIVITY_ID.getKey());
    notificationMessage.setUrl(permanentLinkService.getPermanentLink(new PermanentLinkObject(ActivityPermanentLinkPlugin.OBJECT_TYPE,
                                                                                             activityId)));
    super.process(notificationMessage, notification, localeConfig);
    return notificationMessage;
  }

}
