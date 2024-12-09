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

import java.security.KeyPair;
import java.security.Security;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.exoplatform.commons.utils.MailUtils;

import io.meeds.pwa.storage.PwaNotificationStorage;

import nl.martijndwars.webpush.PushService;

@Configuration
public class PushServiceConfiguration {

  @Value("${pwa.notifications.email:}")
  private String email;

  @Bean
  public PushService getPushService(PwaNotificationStorage pwaNotificationStorage) throws Exception { // NOSONAR
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
    PushService pushService = new PushService(new KeyPair(pwaNotificationStorage.getVapidPublicKey(),
                                                          pwaNotificationStorage.getVapidPrivateKey()));
    pushService.setSubject("mailto:" + getContactEmail());
    return pushService;
  }

  private String getContactEmail() {
    if (StringUtils.isBlank(email)) {
      email = MailUtils.getSenderEmail();
    }
    return email;
  }

}
