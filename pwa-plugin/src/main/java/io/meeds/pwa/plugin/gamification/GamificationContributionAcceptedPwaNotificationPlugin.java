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
package io.meeds.pwa.plugin.gamification;

import static io.meeds.gamification.plugin.RuleTranslationPlugin.RULE_OBJECT_TYPE;
import static io.meeds.gamification.plugin.RuleTranslationPlugin.RULE_TITLE_FIELD_NAME;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import io.meeds.gamification.model.RealizationDTO;
import io.meeds.gamification.service.RealizationService;
import io.meeds.gamification.service.RuleService;
import io.meeds.gamification.utils.Utils;
import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import io.meeds.social.permlink.plugin.ActivityPermanentLinkPlugin;
import io.meeds.social.translation.service.TranslationService;

@Profile("gamification")
@Component
public class GamificationContributionAcceptedPwaNotificationPlugin implements PwaNotificationPlugin {
  private static final String   TITLE_LABEL_KEY = "pwa.notification.GamificationContributionAcceptedNotification.title";

  @Autowired
  private ResourceBundleService resourceBundleService;

  @Autowired
  private PermanentLinkService  permanentLinkService;

  @Autowired
  private RuleService           ruleService;

  @Autowired
  private RealizationService    realizationService;

  @Autowired
  private TranslationService    translationService;

  @Override
  public String getId() {
    return "GamificationContributionAcceptedNotification";
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    notificationMessage.setTitle(resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale()));
    long realizationId = Long.parseLong(notification.getValueOwnerParameter(Utils.REALIZATION_ID_NOTIFICATION_PARAM));
    RealizationDTO realization = realizationService.getRealizationById(realizationId);
    String ruleTitle = translationService.getTranslationLabel(RULE_OBJECT_TYPE,
                                                              realization.getRuleId(),
                                                              RULE_TITLE_FIELD_NAME,
                                                              localeConfig.getLocale());
    if (StringUtils.isBlank(ruleTitle)) {
      ruleTitle = ruleService.findRuleById(realization.getRuleId()).getTitle();
    }
    notificationMessage.setBody(ruleTitle);
    String activityId = notification.getValueOwnerParameter(SocialNotificationUtils.ACTIVITY_ID.getKey());
    notificationMessage.setUrl(permanentLinkService.getPermanentLink(new PermanentLinkObject(ActivityPermanentLinkPlugin.OBJECT_TYPE,
                                                                                             activityId)));
    return notificationMessage;
  }

}
