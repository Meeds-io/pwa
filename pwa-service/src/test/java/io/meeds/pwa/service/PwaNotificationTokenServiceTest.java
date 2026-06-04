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
package io.meeds.pwa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.web.security.hash.SaltedHashService;
import org.exoplatform.web.security.security.CookieTokenService;

import io.meeds.web.security.model.TokenData;
import io.meeds.web.security.storage.PortalTokenStorage;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
public class PwaNotificationTokenServiceTest {

  private static final String TOKEN_VALUE                      = "token";

  private static final String PWA_NOTIFICATIONS_PUSH_TOKEN_TTL = "pwa.notifications.push.token.ttl.seconds";

  private static final String TEST_USER                        = "testUser";

  private static final long   NOTIFICATION_ID                  = 123L;

  private static final String SUBSCRIPTION_ID                  = "subscriptionId";

  private static final String TOKEN_SELECTOR                   = "selector";

  private static final String TOKEN_VALIDATOR                  = "validator";

  @Mock
  private PortalTokenStorage  tokenStore;

  @Mock
  private SaltedHashService   saltedHashService;

  private String              previousTtl;

  private TestTokenService    tokenService;

  @BeforeEach
  @SneakyThrows
  public void setUp() {
    previousTtl = System.getProperty(PWA_NOTIFICATIONS_PUSH_TOKEN_TTL);
    System.setProperty(PWA_NOTIFICATIONS_PUSH_TOKEN_TTL, "3600");
    tokenService = new TestTokenService(tokenStore);
    ReflectionTestUtils.setField(tokenService, "saltedHashService", saltedHashService);
    lenient().when(saltedHashService.getSaltedHash(any()))
             .thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
    lenient().when(saltedHashService.validate(any(), any()))
             .thenAnswer(invocation -> {
               String tokenRandomString = invocation.getArgument(0);
               String hash = invocation.getArgument(1);
               return ("hash:" + tokenRandomString).equals(hash);
             });
  }

  @AfterEach
  public void tearDown() {
    if (previousTtl == null) {
      System.clearProperty(PWA_NOTIFICATIONS_PUSH_TOKEN_TTL);
    } else {
      System.setProperty(PWA_NOTIFICATIONS_PUSH_TOKEN_TTL, previousTtl);
    }
  }

  @Test
  @SneakyThrows
  public void createToken() {
    String token = tokenService.createToken(TEST_USER, NOTIFICATION_ID, SUBSCRIPTION_ID);

    assertEquals(TOKEN_SELECTOR + CookieTokenService.SEPARATOR_CHAR + TOKEN_VALIDATOR, token);

    ArgumentCaptor<TokenData> tokenDataCaptor = ArgumentCaptor.forClass(TokenData.class);
    verify(tokenStore).createToken(tokenDataCaptor.capture());

    TokenData tokenData = tokenDataCaptor.getValue();
    assertEquals(TOKEN_SELECTOR, tokenData.getTokenId());
    assertEquals(TEST_USER, tokenData.getUsername());
    assertEquals(tokenService.buildTokenType(NOTIFICATION_ID, SUBSCRIPTION_ID), tokenData.getTokenType());
    assertNotNull(tokenData.getHash());
    assertTrue(tokenData.getExpirationTime().getTime() > System.currentTimeMillis());
  }

  @Test
  public void createTokenWhenMandatoryParametersAreMissing() {
    assertThrows(IllegalArgumentException.class, () -> tokenService.createToken(null, NOTIFICATION_ID, SUBSCRIPTION_ID));
    assertThrows(IllegalArgumentException.class, () -> tokenService.createToken("", NOTIFICATION_ID, SUBSCRIPTION_ID));
    assertThrows(IllegalArgumentException.class, () -> tokenService.createToken(TEST_USER, NOTIFICATION_ID, null));
    assertThrows(IllegalArgumentException.class, () -> tokenService.createToken(TEST_USER, NOTIFICATION_ID, ""));
  }

  @Test
  public void validateToken() {
    String token = createTokenAndMockLookup();

    assertEquals(TEST_USER, tokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID));
    verify(tokenStore, never()).deleteToken(TOKEN_SELECTOR);
  }

  @Test
  public void validateTokenWhenTokenOrSubscriptionIsBlank() {
    assertNull(tokenService.validateToken(null, NOTIFICATION_ID, SUBSCRIPTION_ID));
    assertNull(tokenService.validateToken("", NOTIFICATION_ID, SUBSCRIPTION_ID));
    assertNull(tokenService.validateToken(TOKEN_VALUE, NOTIFICATION_ID, null));
    assertNull(tokenService.validateToken(TOKEN_VALUE, NOTIFICATION_ID, ""));
  }

  @Test
  public void validateTokenWhenScopeDoesNotMatch() {
    String token = createTokenAndMockLookup();

    assertNull(tokenService.validateToken(token, NOTIFICATION_ID + 1, SUBSCRIPTION_ID));
    assertNull(tokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID + "2"));
  }

  @Test
  public void validateTokenWhenTokenIsExpired() {
    String token = tokenService.createToken(TEST_USER, NOTIFICATION_ID, SUBSCRIPTION_ID);
    TokenData tokenData = captureCreatedToken();
    TokenData expiredTokenData = new TokenData(TOKEN_SELECTOR,
                                               tokenData.getHash(),
                                               TEST_USER,
                                               new Date(System.currentTimeMillis() - 1000),
                                               tokenService.buildTokenType(NOTIFICATION_ID, SUBSCRIPTION_ID));
    when(tokenStore.getToken(TOKEN_SELECTOR)).thenReturn(expiredTokenData);

    assertNull(tokenService.validateToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID));
    verify(tokenStore).deleteToken(TOKEN_SELECTOR);
  }

  @Test
  public void consumeToken() {
    String token = createTokenAndMockLookup();

    assertEquals(TEST_USER, tokenService.consumeToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID));
    verify(tokenStore).deleteToken(TOKEN_SELECTOR);
  }

  @Test
  public void consumeTokenWhenScopeDoesNotMatch() {
    String token = createTokenAndMockLookup();

    assertNull(tokenService.consumeToken(token, NOTIFICATION_ID + 1, SUBSCRIPTION_ID));
    verify(tokenStore, never()).deleteToken(TOKEN_SELECTOR);
  }

  @Test
  public void deleteNotificationToken() {
    String token = createTokenAndMockLookup();

    tokenService.deleteNotificationToken(token, NOTIFICATION_ID, SUBSCRIPTION_ID);

    verify(tokenStore).deleteToken(TOKEN_SELECTOR);
  }

  @Test
  public void deleteNotificationTokenWhenParametersAreBlank() {
    tokenService.deleteNotificationToken(null, NOTIFICATION_ID, SUBSCRIPTION_ID);
    tokenService.deleteNotificationToken("", NOTIFICATION_ID, SUBSCRIPTION_ID);
    tokenService.deleteNotificationToken(TOKEN_VALUE, NOTIFICATION_ID, null);
    tokenService.deleteNotificationToken(TOKEN_VALUE, NOTIFICATION_ID, "");

    verify(tokenStore, never()).deleteToken(any());
  }

  @Test
  public void deleteNotificationTokens() {
    tokenService.deleteNotificationTokens(TEST_USER);

    verify(tokenStore).deleteTokensByUsernameAndType(TEST_USER, PwaNotificationTokenService.TOKEN_TYPE_PREFIX);
  }

  @Test
  public void deleteNotificationTokensWhenUsernameIsBlank() {
    tokenService.deleteNotificationTokens(null);
    tokenService.deleteNotificationTokens("");

    verify(tokenStore, never()).deleteTokensByUsernameAndType(any(), any());
  }

  @Test
  public void buildTokenType() {
    String tokenType = tokenService.buildTokenType(NOTIFICATION_ID, SUBSCRIPTION_ID);

    assertTrue(tokenType.startsWith(PwaNotificationTokenService.TOKEN_TYPE_PREFIX + ":"));
    assertNotEquals(tokenType, tokenService.buildTokenType(NOTIFICATION_ID + 1, SUBSCRIPTION_ID));
    assertNotEquals(tokenType, tokenService.buildTokenType(NOTIFICATION_ID, SUBSCRIPTION_ID + "2"));
  }

  private String createTokenAndMockLookup() {
    String token = tokenService.createToken(TEST_USER, NOTIFICATION_ID, SUBSCRIPTION_ID);
    TokenData tokenData = captureCreatedToken();
    when(tokenStore.getToken(TOKEN_SELECTOR)).thenReturn(tokenData);
    return token;
  }

  @SneakyThrows
  private TokenData captureCreatedToken() {
    ArgumentCaptor<TokenData> tokenDataCaptor = ArgumentCaptor.forClass(TokenData.class);
    verify(tokenStore).createToken(tokenDataCaptor.capture());
    return tokenDataCaptor.getValue();
  }

  private static class TestTokenService extends PwaNotificationTokenService {

    private final AtomicInteger index = new AtomicInteger();

    private TestTokenService(PortalTokenStorage tokenStore) {
      super(tokenStore);
    }

    @Override
    protected String nextRandom() {
      return index.getAndIncrement() % 2 == 0 ? TOKEN_SELECTOR : TOKEN_VALIDATOR;
    }
  }
}
