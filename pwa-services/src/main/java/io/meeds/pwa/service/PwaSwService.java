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
package io.meeds.pwa.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.portal.application.ResourceRequestFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

@Service
public class PwaSwService {

  private static final Log               LOG                       = ExoLogger.getLogger(PwaSwService.class);

  private static final boolean           DEVELOPPING               = PropertyManager.isDevelopping();

  private static final String            VAPID_PUBLIC_KEY_VARIABLE = "@vapidPublicKey@";

  private static final String            DEVELOPMENT_VARIABLE      = "@development@";

  private static final String            ASSETS_VERSION_VARIABLE   = "@assets-version@";

  private static AtomicReference<String> serviceWorkerContent      = new AtomicReference<>();

  @Autowired
  private PwaNotificationService         pwaNotificationService;

  @Autowired
  private ConfigurationManager           configurationManager;

  @Value("${pwa.service.worker.path:jar:/pwa/service-worker.js}")
  private String                         serviceWorkerPath;

  public String getContent() {
    if (serviceWorkerContent.get() == null || DEVELOPPING) {
      try (InputStream is = configurationManager.getInputStream(serviceWorkerPath)) {
        String content = IOUtils.toString(is, StandardCharsets.UTF_8);
        content = replaceVariables(content);
        serviceWorkerContent.set(content);
      } catch (Exception e) {
        LOG.warn("Can't find service worker path: {}", serviceWorkerPath, e);
        return null;
      }
    }
    return serviceWorkerContent.get();
  }

  private String replaceVariables(String content) {
    content = content.replace(VAPID_PUBLIC_KEY_VARIABLE, pwaNotificationService.getVapidPublicKeyString().replace("=", ""));
    content = content.replace(ASSETS_VERSION_VARIABLE, ResourceRequestFilter.version);
    content = content.replace(DEVELOPMENT_VARIABLE, String.valueOf(DEVELOPPING));
    return content;
  }

}
