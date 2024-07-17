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
package io.meeds.pwa.plugin;

import static org.exoplatform.commons.api.notification.NotificationConstants.DEFAULT_SUBJECT_KEY;

import java.text.MessageFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.config.PluginConfig;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.portal.Constants;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.services.resources.LocaleContextInfo;

import io.meeds.pwa.model.PwaNotificationMessage;

@Component
public class DefaultPwaNotificationPlugin implements PwaNotificationPlugin {

  private static final Log     LOG = ExoLogger.getLogger(DefaultPwaNotificationPlugin.class);

  @Autowired
  private OrganizationService  organizationService;

  @Autowired
  private LocaleConfigService  localeConfigService;

  @Autowired
  private PluginSettingService pluginSettingService;

  @Override
  public PwaNotificationMessage process(NotificationInfo notification) {
    PluginKey pluginKey = notification.getKey();
    String pluginId = pluginKey.getId();
    PluginConfig templateConfig = pluginSettingService.getPluginConfig(pluginId);
    String bundlePath = templateConfig.getBundlePath();
    String subjectKey = templateConfig.getKeyValue(PluginConfig.SUBJECT_KEY, getDefaultKey(DEFAULT_SUBJECT_KEY, pluginId));
    Locale locale = getLocale(notification);
    String title = TemplateUtils.getResourceBundle(subjectKey, locale, bundlePath);
    PwaNotificationMessage pwaNotificationMessage = new PwaNotificationMessage();
    pwaNotificationMessage.setTag(notification.getId());
    pwaNotificationMessage.setTitle(title);
    pwaNotificationMessage.setLang(locale.getLanguage());
    pwaNotificationMessage.setRequireInteraction(true);
    LocaleConfig localeConfig = localeConfigService.getLocaleConfig(LocaleContextInfo.getLocaleAsString(locale));
    pwaNotificationMessage.setDir(localeConfig == null || localeConfig.getOrientation() == null
                                  || localeConfig.getOrientation().isLT() ? "ltr" : "rtl");
    return pwaNotificationMessage;
  }

  private String getDefaultKey(String key, String providerId) {
    return MessageFormat.format(key, providerId);
  }

  private Locale getLocale(NotificationInfo notification) {
    return Locale.forLanguageTag(getLanguage(notification.getTo()));
  }

  private String getLanguage(String username) {
    try {
      UserProfile userProfile = organizationService.getUserProfileHandler().findUserProfileByName(username);
      String language = userProfile == null ? null : userProfile.getAttribute(Constants.USER_LANGUAGE);
      return language == null ? localeConfigService.getDefaultLocaleConfig().getLanguage() : language;
    } catch (Exception e) {
      String defaultLanguage = localeConfigService.getDefaultLocaleConfig().getLanguage();
      LOG.warn("Error retrieving user {} language, use default language {}", username, defaultLanguage);
      return defaultLanguage;
    }
  }

}
