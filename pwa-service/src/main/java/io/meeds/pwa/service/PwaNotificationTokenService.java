/*
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
package io.meeds.pwa.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.web.security.PortalToken;
import org.exoplatform.web.security.security.CookieTokenService;

import io.meeds.web.security.storage.PortalTokenStorage;

/**
 * Persistent token service used by service workers to retrieve a web
 * notification after the web session cookie has expired. The generated token is
 * persisted through {@link PortalTokenStorage} by the parent
 * {@link CookieTokenService}; only the token selector and salted hash are
 * stored. The token type binds the token to both the notification and the push
 * subscription, so the same raw token cannot be used for another notification
 * or another device subscription. The service also exposes consumeToken(...)
 * for single-push delivery tokens: after the first successful push fetch, the
 * token is deleted from the persistent store.
 */
@Service
public class PwaNotificationTokenService extends CookieTokenService {

  public static final String TOKEN_TYPE_PREFIX = "pwa-notification";

  public PwaNotificationTokenService(PortalTokenStorage tokenStore) {
    super(initParams(), tokenStore);
  }

  public String createToken(String username, long notificationId, String subscriptionId) {
    if (StringUtils.isBlank(username)) {
      throw new IllegalArgumentException("username is mandatory");
    }
    if (StringUtils.isBlank(subscriptionId)) {
      throw new IllegalArgumentException("subscriptionId is mandatory");
    }
    return super.createToken(username, buildTokenType(notificationId, subscriptionId));
  }

  public String validateToken(String token, long notificationId, String subscriptionId) {
    return validateToken(token, notificationId, subscriptionId, false);
  }

  public String consumeToken(String token, long notificationId, String subscriptionId) {
    return validateToken(token, notificationId, subscriptionId, true);
  }

  public String validateToken(String token, long notificationId, String subscriptionId, boolean remove) {
    if (StringUtils.isBlank(token) || StringUtils.isBlank(subscriptionId)) {
      return null;
    }
    String tokenType = buildTokenType(notificationId, subscriptionId);
    PortalToken portalToken = remove ? super.deleteToken(token, tokenType) : super.getToken(token, tokenType);
    if (portalToken == null) {
      return null;
    }
    if (portalToken.getExpirationTimeMillis() > System.currentTimeMillis()) {
      return portalToken.getUsername();
    } else if (!remove) {
      super.deleteToken(token, tokenType);
    }
    return null;
  }

  public void deleteNotificationToken(String token, long notificationId, String subscriptionId) {
    if (StringUtils.isNotBlank(token) && StringUtils.isNotBlank(subscriptionId)) {
      super.deleteToken(token, buildTokenType(notificationId, subscriptionId));
    }
  }

  public void deleteNotificationTokens(String username) {
    if (StringUtils.isNotBlank(username)) {
      super.deleteTokensByUsernameAndType(username, TOKEN_TYPE_PREFIX);
    }
  }

  protected String buildTokenType(long notificationId, String subscriptionId) {
    return TOKEN_TYPE_PREFIX + ":" + scopeHash(notificationId, subscriptionId);
  }

  private static InitParams initParams() {
    InitParams initParams = new InitParams();
    ValuesParam valuesParam = new ValuesParam();
    valuesParam.setName("service.configuration");
    valuesParam.setValues(List.of(TOKEN_TYPE_PREFIX,
                                  String.valueOf(TimeUnit.SECONDS.toHours(Long.parseLong(System.getProperty("pwa.notifications.push.token.ttl.seconds",
                                                                                                            "28800")))),
                                  "HOUR"));
    initParams.addParam(valuesParam);
    ValueParam valueParam = new ValueParam();
    valueParam.setName("cleanup.period.time");
    valueParam.setValue("3600");
    initParams.addParam(valueParam);
    return initParams;
  }

  private String scopeHash(long notificationId, String subscriptionId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest((notificationId + ":" + subscriptionId).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }
}
