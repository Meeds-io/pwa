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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;

import jakarta.servlet.Filter;
import lombok.SneakyThrows;

@SpringBootTest(classes = { PwaNotificationRest.class, PortalAuthenticationManager.class, })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class PwaNotificationRestTest {

  private static final String ACTION_PARAM    = "action";

  private static final long   NOTIFICATION_ID = 155;

  private static final String REST_PATH       = "/notifications"; // NOSONAR

  private static final String SIMPLE_USER     = "simple";

  private static final String TEST_PASSWORD   = "testPassword";

  static final ObjectMapper   OBJECT_MAPPER;

  static {
    // Workaround when Jackson is defined in shared library with different
    // version and without artifact jackson-datatype-jsr310
    OBJECT_MAPPER = JsonMapper.builder()
                              .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
                              .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                              .build();
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @MockBean
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
    ResultActions response = mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()));
    response.andExpect(status().isNotFound());
  }

  @Test
  void getNotificationWhenNotPermitted() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenThrow(IllegalAccessException.class);
    ResultActions response = mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()));
    response.andExpect(status().isForbidden());
  }

  @Test
  void getNotification() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenReturn(pwaNotificationMessage);
    ResultActions response = mockMvc.perform(get(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser()));
    response.andExpect(status().isOk());
  }

  @Test
  void updateNotificationPropertyWhenNotFound() throws Exception {
    doThrow(ObjectNotFoundException.class).when(pwaNotificationService)
                                          .updateNotification(NOTIFICATION_ID,
                                                              PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                              SIMPLE_USER);
    ResultActions response = mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                                     .formField(ACTION_PARAM,
                                                                                                PWA_NOTIFICATION_MARK_READ_USER_ACTION));
    response.andExpect(status().isNotFound());
  }

  @Test
  void updateNotificationPropertyWhenNotPermitted() throws Exception {
    doThrow(IllegalAccessException.class).when(pwaNotificationService)
                                         .updateNotification(NOTIFICATION_ID,
                                                             PWA_NOTIFICATION_MARK_READ_USER_ACTION,
                                                             SIMPLE_USER);
    ResultActions response = mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                                     .formField(ACTION_PARAM,
                                                                                                PWA_NOTIFICATION_MARK_READ_USER_ACTION));
    response.andExpect(status().isForbidden());
  }

  @Test
  void updateNotificationProperty() throws Exception {
    when(pwaNotificationService.getNotification(NOTIFICATION_ID, SIMPLE_USER)).thenReturn(pwaNotificationMessage);
    ResultActions response = mockMvc.perform(patch(REST_PATH + "/" + NOTIFICATION_ID).with(testSimpleUser())
                                                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                                                                     .formField(ACTION_PARAM,
                                                                                                PWA_NOTIFICATION_MARK_READ_USER_ACTION));
    response.andExpect(status().isOk());
    verify(pwaNotificationService).updateNotification(NOTIFICATION_ID, PWA_NOTIFICATION_MARK_READ_USER_ACTION, SIMPLE_USER);
  }

  private RequestPostProcessor testSimpleUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD)
                            .authorities(new SimpleGrantedAuthority("users"));
  }

  @SneakyThrows
  public static String asJsonString(final Object obj) {
    return OBJECT_MAPPER.writeValueAsString(obj);
  }

}
