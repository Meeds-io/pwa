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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.config.PluginConfig;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.services.resources.LocaleConfig;

import io.meeds.pwa.model.PwaNotificationMessage;

@Component
public class DefaultPwaNotificationPlugin implements PwaNotificationPlugin {

  @Autowired
  private PluginSettingService pluginSettingService;

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PluginKey pluginKey = notification.getKey();
    String pluginId = pluginKey.getId();
    PluginConfig templateConfig = pluginSettingService.getPluginConfig(pluginId);
    String bundlePath = templateConfig.getBundlePath();
    String subjectKey = templateConfig.getKeyValue(PluginConfig.SUBJECT_KEY, getDefaultKey(DEFAULT_SUBJECT_KEY, pluginId));
    String title = TemplateUtils.getResourceBundle(subjectKey, localeConfig.getLocale(), bundlePath);
    PwaNotificationMessage pwaNotificationMessage = new PwaNotificationMessage();
    pwaNotificationMessage.setTitle(title);
    return pwaNotificationMessage;
  }

  private String getDefaultKey(String key, String providerId) {
    return MessageFormat.format(key, providerId);
  }

}
