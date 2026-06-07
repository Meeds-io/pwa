package io.meeds.pwa.rest;

import static io.meeds.pwa.service.PwaNotificationService.PWA_NOTIFICATION_MARK_READ_USER_ACTION;
import static org.mockito.Mockito.doThrow;

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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;

import jakarta.servlet.Filter;

@SpringBootTest(classes = { PwaNotificationRest.class, PortalAuthenticationManager.class, })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class PwaNotificationRestTest {

  private static final String    PUSH_PATH_KEY            = "/push";                                                                       // NOSONAR

  private static final String    AUTHORIZATION_HEADER_KEY = "Authorization";

  private static final String    ACTION_PARAM             = "action";

  private static final String    AUTHORIZATION_HEADER     =
                                                      "PWA-Notification token=token,subscriptionId=subscriptionId,timestamp=1,proof=proof";

  private static final long      NOTIFICATION_ID          = 155;

  private static final long      SENT_AT                  = 1000;

  private static final long      RECEIVED_AT              = 65000;

  private static final long      DELAY_MS                 = RECEIVED_AT - SENT_AT;

  private static final String    REST_PATH                = "/notifications";                                                              // NOSONAR

  private static final String    SIMPLE_USER              = "simple";

  private static final String    SUBSCRIPTION_ID          = "subscriptionId";

  private static final String    TEST_PASSWORD            = "testPassword";

  @MockitoBean
  private PwaNotificationService pwaNotificationService;

  @Autowired
  private SecurityFilterChain    filterChain;

  @Autowired
  private WebApplicationContext  context;

  @Mock
  private PwaNotificationMessage pwaNotificationMessage;

  private MockMvc                mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
                             .addFilters(filterChain.getFilters().toArray(new Filter[0]))
                             .build();
  }

  @Test
  void getNotificationWhenNotFound() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenThrow(ObjectNotFoundException.class);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()))
           .andExpect(status().isNotFound());
  }

  @Test
  void getNotificationWhenNotPermitted() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenThrow(IllegalAccessException.class);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()))
           .andExpect(status().isForbidden());
  }

  @Test
  void getNotification() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenReturn(pwaNotificationMessage);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()))
           .andExpect(status().isOk());
  }

  @Test
  void getNotificationFromPushWhenNotFound() throws Exception {
    when(pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                        AUTHORIZATION_HEADER,
                                                        SIMPLE_USER)).thenThrow(ObjectNotFoundException.class);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                          .header(AUTHORIZATION_HEADER_KEY, AUTHORIZATION_HEADER))
           .andExpect(status().isNotFound());
  }

  @Test
  void getNotificationFromPushWhenNotPermitted() throws Exception {
    when(pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                        AUTHORIZATION_HEADER,
                                                        SIMPLE_USER)).thenThrow(IllegalAccessException.class);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                          .header(AUTHORIZATION_HEADER_KEY, AUTHORIZATION_HEADER))
           .andExpect(status().isForbidden());
  }

  @Test
  void getNotificationFromPush() throws Exception {
    when(pwaNotificationService.getNotificationFromPush(NOTIFICATION_ID,
                                                        AUTHORIZATION_HEADER,
                                                        SIMPLE_USER)).thenReturn(pwaNotificationMessage);
    mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                          .header(AUTHORIZATION_HEADER_KEY, AUTHORIZATION_HEADER))
           .andExpect(status().isOk());
  }

  @Test
  void updateNotificationPropertyWhenNotFound() throws Exception {
    doThrow(ObjectNotFoundException.class).when(pwaNotificationService)
                                          .updateNotification(NOTIFICATION_ID,
                                                              PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                              SIMPLE_USER);
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                            .formField(ACTION_PARAM, PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isNotFound());
  }

  @Test
  void updateNotificationPropertyWhenNotPermitted() throws Exception {
    doThrow(IllegalAccessException.class).when(pwaNotificationService)
                                         .updateNotification(NOTIFICATION_ID,
                                                             PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                             SIMPLE_USER);
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                            .formField(ACTION_PARAM, PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isForbidden());
  }

  @Test
  void updateNotificationProperty() throws Exception {
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                            .formField(ACTION_PARAM, PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isOk());
    verify(pwaNotificationService).updateNotification(NOTIFICATION_ID, PWA_NOTIFICATION_MARK_READ_USER_ACTION, SIMPLE_USER);
  }

  @Test
  void updateNotificationPropertyFromPushWhenNotFound() throws Exception {
    doThrow(ObjectNotFoundException.class).when(pwaNotificationService)
                                          .updateNotificationFromPush(NOTIFICATION_ID,
                                                                      PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                                      AUTHORIZATION_HEADER,
                                                                      SIMPLE_USER);
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                            .header(AUTHORIZATION_HEADER_KEY,
                                                                                    AUTHORIZATION_HEADER)
                                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                            .formField(ACTION_PARAM,
                                                                                       PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isNotFound());
  }

  @Test
  void updateNotificationPropertyFromPushWhenNotPermitted() throws Exception {
    doThrow(IllegalAccessException.class).when(pwaNotificationService)
                                         .updateNotificationFromPush(NOTIFICATION_ID,
                                                                     PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                                     AUTHORIZATION_HEADER,
                                                                     SIMPLE_USER);
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                            .header(AUTHORIZATION_HEADER_KEY,
                                                                                    AUTHORIZATION_HEADER)
                                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                            .formField(ACTION_PARAM,
                                                                                       PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isForbidden());
  }

  @Test
  void updateNotificationPropertyFromPush() throws Exception {
    mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID + PUSH_PATH_KEY).with(testSimpleUser())
                                                                            .header(AUTHORIZATION_HEADER_KEY,
                                                                                    AUTHORIZATION_HEADER)
                                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                            .formField(ACTION_PARAM,
                                                                                       PWA_NOTIFICATION_MARK_READ_USER_ACTION))
           .andExpect(status().isOk());
    verify(pwaNotificationService).updateNotificationFromPush(NOTIFICATION_ID,
                                                              PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                              AUTHORIZATION_HEADER,
                                                              SIMPLE_USER);
  }

  @Test
  void reportPushDeliveryDelayWhenNotFound() throws Exception {
    doThrow(ObjectNotFoundException.class).when(pwaNotificationService)
                                          .reportPushDeliveryDelay(NOTIFICATION_ID,
                                                                   AUTHORIZATION_HEADER,
                                                                   SIMPLE_USER,
                                                                   SENT_AT,
                                                                   RECEIVED_AT);
    performReportPushDeliveryDelay().andExpect(status().isNotFound());
  }

  @Test
  void reportPushDeliveryDelayWhenNotPermitted() throws Exception {
    doThrow(IllegalAccessException.class).when(pwaNotificationService)
                                         .reportPushDeliveryDelay(NOTIFICATION_ID,
                                                                  AUTHORIZATION_HEADER,
                                                                  SIMPLE_USER,
                                                                  SENT_AT,
                                                                  RECEIVED_AT);
    performReportPushDeliveryDelay().andExpect(status().isForbidden());
  }

  @Test
  void reportPushDeliveryDelay() throws Exception {
    performReportPushDeliveryDelay().andExpect(status().isOk());
    verify(pwaNotificationService).reportPushDeliveryDelay(NOTIFICATION_ID,
                                                           AUTHORIZATION_HEADER,
                                                           SIMPLE_USER,
                                                           SENT_AT,
                                                           RECEIVED_AT);
  }

  @Test
  void getPushDeliveryDelayStatus() throws Exception {
    when(pwaNotificationService.getPushDeliveryDelayStatus(SIMPLE_USER, SUBSCRIPTION_ID)).thenReturn(Map.of("delayMs", DELAY_MS));
    mockMvc.perform(get(REST_PATH + "/push/delivery-delay/" + SUBSCRIPTION_ID).with(testSimpleUser()))
           .andExpect(status().isOk());
    verify(pwaNotificationService).getPushDeliveryDelayStatus(SIMPLE_USER, SUBSCRIPTION_ID);
  }

  @Test
  void resetPushDeliveryDelayStatus() throws Exception {
    mockMvc.perform(delete(REST_PATH + "/push/delivery-delay/" + SUBSCRIPTION_ID).with(testSimpleUser()))
           .andExpect(status().isOk());
    verify(pwaNotificationService).resetPushDeliveryDelay(SIMPLE_USER, SUBSCRIPTION_ID);
  }

  private ResultActions performReportPushDeliveryDelay() throws Exception {
    return mockMvc.perform(post(REST_PATH + "/" + NOTIFICATION_ID + "/push/delivery-delay").with(testSimpleUser())
                                                                                           .header(AUTHORIZATION_HEADER_KEY,
                                                                                                   AUTHORIZATION_HEADER)
                                                                                           .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                                           .formField("sentAt",
                                                                                                      String.valueOf(SENT_AT))
                                                                                           .formField("receivedAt",
                                                                                                      String.valueOf(RECEIVED_AT))
                                                                                           .formField("delayMs",
                                                                                                      String.valueOf(DELAY_MS)));
  }

  private RequestPostProcessor testSimpleUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD)
                            .authorities(new SimpleGrantedAuthority("users"));
  }

}
