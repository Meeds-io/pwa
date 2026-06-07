/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
package io.meeds.pwa.upgrade;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.thumbnail.ImageThumbnailService;

import io.meeds.pwa.model.ManifestIcon;
import io.meeds.pwa.service.PwaManifestService;

/**
 * A component plugin to clean Old Version's thumbnailIcon
 */
@Component
public class PwaManifestImageUpgrade extends UpgradeProductPlugin {

  private PwaManifestService    pwaManifestService;

  private ImageThumbnailService imageThumbnailService;

  public PwaManifestImageUpgrade(SettingService settingService,
                                 PwaManifestService pwaManifestService,
                                 ImageThumbnailService imageThumbnailService) {
    super(settingService, initParams());
    this.pwaManifestService = pwaManifestService;
    this.imageThumbnailService = imageThumbnailService;
  }

  @Override
  public String getName() {
    return "PwaManifestImageUpgrade";
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return StringUtils.isNotBlank(previousVersion)
           && !StringUtils.equals(previousVersion, "0")
           && !StringUtils.equals(previousVersion, newVersion);
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    deleteThumbnails(pwaManifestService.getLargeIcon());
    deleteThumbnails(pwaManifestService.getSmallIcon());
  }

  private void deleteThumbnails(ManifestIcon manifestIcon) {
    if (manifestIcon != null && manifestIcon.getFileId() > 0) {
      imageThumbnailService.deleteThumbnails(manifestIcon.getFileId());
    }
  }

  private static InitParams initParams() {
    InitParams initParams = new InitParams();
    addValueParam(initParams,
                  "product.group.id",
                  "org.exoplatform.social");
    addValueParam(initParams,
                  "plugin.execution.order",
                  "200");
    addValueParam(initParams,
                  "plugin.upgrade.execute.once",
                  "true");
    addValueParam(initParams,
                  "enabled",
                  "true");
    return initParams;
  }

  private static void addValueParam(InitParams initParams, String key, String value) {
    ValueParam valueParam = new ValueParam();
    valueParam.setName(key);
    valueParam.setValue(value);
    initParams.addParam(valueParam);
  }

}
