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
package io.meeds.pwa.plugin.social;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import io.meeds.social.permlink.plugin.SpacePermanentLinkPlugin;

import lombok.Getter;

@Component
public class SpaceJoinRequestReceivedPwaNotificationPlugin implements PwaNotificationPlugin {

  private static final String   TITLE_LABEL_KEY = "pwa.notification.RequestJoinSpacePlugin.title";

  @Autowired
  private ResourceBundleService resourceBundleService;

  @Autowired
  private PermanentLinkService  permanentLinkService;

  @Autowired
  @Getter
  private IdentityManager       identityManager;

  @Autowired
  @Getter
  private SpaceService          spaceService;

  @Override
  public String getId() {
    return "RequestJoinSpacePlugin";
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    String spaceId = notification.getValueOwnerParameter(SocialNotificationUtils.SPACE_ID.getKey());
    notificationMessage.setTitle(resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale())
                                                      .replace("{0}",
                                                               getFullName(notification.getValueOwnerParameter(SocialNotificationUtils.REQUEST_FROM.getKey())))
                                                      .replace("{1}",
                                                               getSpaceName(spaceId)));
    PermanentLinkObject object = new PermanentLinkObject(SpacePermanentLinkPlugin.OBJECT_TYPE, spaceId);
    object.addParameter(SpacePermanentLinkPlugin.APPLICATION_URI, "members");
    object.addParameter(SpacePermanentLinkPlugin.URI_HASH, "pending");
    notificationMessage.setUrl(permanentLinkService.getPermanentLink(object));
    return notificationMessage;
  }

}
