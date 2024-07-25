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
package io.meeds.pwa.rest;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.service.PwaSubscriptionService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;

import jakarta.servlet.Filter;
import lombok.SneakyThrows;

@SpringBootTest(classes = { PwaPushSubscriptionRest.class, PortalAuthenticationManager.class, })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class PwaPushSubscriptionRestTest {

  private static final String REST_PATH     = "/subscriptions"; // NOSONAR

  private static final String SIMPLE_USER   = "simple";

  private static final String TEST_PASSWORD = "testPassword";

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
  private PwaSubscriptionService pwaSubscriptionService;

  @Autowired
  private SecurityFilterChain    filterChain;

  @Autowired
  private WebApplicationContext  context;

  private MockMvc                mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
                             .addFilters(filterChain.getFilters().toArray(new Filter[0]))
                             .build();
  }

  @Test
  void subscribe() throws Exception {
    UserPushSubscription userPushSubscription = new UserPushSubscription();
    userPushSubscription.setId("1");
    userPushSubscription.setEndpoint("endpoint");
    ResultActions response = mockMvc.perform(post(REST_PATH).with(testSimpleUser())
                                                            .content(asJsonString(userPushSubscription))
                                                            .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    userPushSubscription.setDeviceType("Robot");
    verify(pwaSubscriptionService).createSubscription(userPushSubscription, SIMPLE_USER);
  }

  @Test
  void unsubscribe() throws Exception {
    UserPushSubscription userPushSubscription = new UserPushSubscription();
    userPushSubscription.setId("1");
    ResultActions response = mockMvc.perform(delete(REST_PATH).with(testSimpleUser())
                                                              .content(asJsonString(userPushSubscription))
                                                              .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    verify(pwaSubscriptionService).deleteSubscription(userPushSubscription.getId(), SIMPLE_USER);
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
