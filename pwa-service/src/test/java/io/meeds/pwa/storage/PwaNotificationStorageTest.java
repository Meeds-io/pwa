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
package io.meeds.pwa.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.web.security.codec.CodecInitializer;

import io.meeds.social.util.JsonUtils;

@SpringBootTest(classes = {
  PwaNotificationStorage.class,
})
public class PwaNotificationStorageTest {

  private static final String    PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY = "PWA_PUSH_EXCESSIVE_DELIVERY_DELAY-";

  private static final String    DETECTED_AT_KEY                       = "detectedAt";

  private static final String    DELAY_MS_KEY                          = "delayMs";

  private static final String    SUBSCRIPTION_ID_KEY                   = "subscriptionId";

  private static final String    TEST_USER                             = "testUser";

  private static final String    SUBSCRIPTION_ID                       = "222333658";

  @MockitoBean
  private SettingService         settingService;

  @MockitoBean
  private CodecInitializer       codecInitializer;

  @Autowired
  private PwaNotificationStorage pwaNotificationStorage;

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void getPushDeliveryDelayStatus() {
    Map<String, Object> status = Map.of(SUBSCRIPTION_ID_KEY,
                                        SUBSCRIPTION_ID,
                                        DETECTED_AT_KEY,
                                        System.currentTimeMillis(),
                                        DELAY_MS_KEY,
                                        60000L);
    when(settingService.get(any(),
                            any(),
                            eq(PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY + SUBSCRIPTION_ID)))
                                                                                         .thenReturn((SettingValue) SettingValue.create(JsonUtils.toJsonString(status)));

    Map<String, Object> result = pwaNotificationStorage.getPushDeliveryDelayStatus(TEST_USER,
                                                                                   SUBSCRIPTION_ID,
                                                                                   TimeUnit.HOURS.toMillis(1));

    assertEquals(SUBSCRIPTION_ID, result.get(SUBSCRIPTION_ID_KEY));
    assertEquals(60000L, Long.parseLong(result.get(DELAY_MS_KEY).toString()));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void getPushDeliveryDelayStatusWhenExpired() {
    Map<String, Object> status = Map.of(SUBSCRIPTION_ID_KEY,
                                        SUBSCRIPTION_ID,
                                        DETECTED_AT_KEY,
                                        System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
                                        DELAY_MS_KEY,
                                        60000L);
    when(settingService.get(any(),
                            any(),
                            eq(PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY + SUBSCRIPTION_ID)))
                                                                                         .thenReturn((SettingValue) SettingValue.create(JsonUtils.toJsonString(status)));

    Map<String, Object> result = pwaNotificationStorage.getPushDeliveryDelayStatus(TEST_USER,
                                                                                   SUBSCRIPTION_ID,
                                                                                   TimeUnit.HOURS.toMillis(1));

    assertTrue(result.isEmpty());
    verify(settingService).remove(any(), any(), eq(PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY + SUBSCRIPTION_ID));
  }

  @Test
  public void resetPushDeliveryDelay() {
    pwaNotificationStorage.resetPushDeliveryDelay(TEST_USER, SUBSCRIPTION_ID);
    verify(settingService).remove(any(), any(), eq(PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY + SUBSCRIPTION_ID));
  }

  @Test
  public void recordExcessivePushDeliveryDelay() {
    pwaNotificationStorage.recordExcessivePushDeliveryDelay(TEST_USER, SUBSCRIPTION_ID, 60000L);
    verify(settingService).set(any(), any(), eq(PWA_PUSH_EXCESSIVE_DELIVERY_DELAY_KEY + SUBSCRIPTION_ID), any());
  }

}
