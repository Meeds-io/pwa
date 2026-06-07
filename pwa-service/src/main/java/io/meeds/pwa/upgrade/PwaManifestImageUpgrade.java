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

import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.services.thumbnail.ImageThumbnailService;

import io.meeds.common.ContainerTransactional;
import io.meeds.pwa.model.ManifestIcon;
import io.meeds.pwa.service.PwaManifestService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * A component plugin to clean Old Version's thumbnailIcon
 */
@Component
@Slf4j
public class PwaManifestImageUpgrade {

  private static final Context  CONTEXT = Context.GLOBAL.id("PwaManifestImageUpgrade");

  private static final Scope    SCOPE   = Scope.APPLICATION.id("PWA");

  private static final String   KEY     = "PwaManifestImage-Upgrade-V1";

  private SettingService        settingService;

  private PwaManifestService    pwaManifestService;

  private ImageThumbnailService imageThumbnailService;

  public PwaManifestImageUpgrade(SettingService settingService,
                                 PwaManifestService pwaManifestService,
                                 ImageThumbnailService imageThumbnailService) {
    this.settingService = settingService;
    this.pwaManifestService = pwaManifestService;
    this.imageThumbnailService = imageThumbnailService;
  }

  @PostConstruct
  @ContainerTransactional
  public void init() {
    if (settingService.get(CONTEXT, SCOPE, KEY) == null) {
      processUpgrade();
      settingService.set(CONTEXT,
                         SCOPE,
                         KEY,
                         SettingValue.create("true"));
      log.info("PWA Manifest Thumbnail cleared");
    }
  }

  public void processUpgrade() {
    deleteThumbnails(pwaManifestService.getLargeIcon());
    deleteThumbnails(pwaManifestService.getSmallIcon());
  }

  private void deleteThumbnails(ManifestIcon manifestIcon) {
    if (manifestIcon != null && manifestIcon.getFileId() > 0) {
      imageThumbnailService.deleteThumbnails(manifestIcon.getFileId());
    }
  }

}
