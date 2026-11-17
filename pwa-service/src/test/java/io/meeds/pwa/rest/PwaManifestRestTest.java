/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.meeds.pwa.model.ManifestIcon;
import io.meeds.pwa.service.PwaManifestService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;

import jakarta.servlet.Filter;

@SpringBootTest(classes = { PwaManifestRest.class, PortalAuthenticationManager.class, })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
public class PwaManifestRestTest {

  private static final String   ICON_PATH   = "/manifest/largeIcon/%s/512.png"; // NOSONAR

  private static final String   FALLBACK_CACHE = "max-age=60";

  private static final byte[]   PNG         = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

  private static final byte[]   SVG         = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"/>"
                                                                                                          .getBytes(StandardCharsets.UTF_8);

  @MockitoBean
  private PwaManifestService    pwaManifestService;

  @Autowired
  private SecurityFilterChain   filterChain;

  @Autowired
  private WebApplicationContext context;

  private MockMvc               mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
                             .addFilters(filterChain.getFilters().toArray(new Filter[0]))
                             .build();
  }

  @Test
  void largeIconOfTheCurrentFileIsCachedForGood() throws Exception {
    mockIcons(PNG, 42L);

    ResultActions response = mockMvc.perform(get(ICON_PATH.formatted("42")));

    response.andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("ETag", "\"900\""))
            .andExpect(header().string("Cache-Control", "max-age=31536000, public"))
            .andExpect(header().exists("Expires"))
            .andExpect(header().doesNotExist("Content-Security-Policy"));
  }

  @Test
  void largeIconOfAnotherFileIsCachedAMinuteAtMost() throws Exception {
    mockIcons(PNG, 42L);

    ResultActions response = mockMvc.perform(get(ICON_PATH.formatted("41")));

    response.andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", FALLBACK_CACHE))
            .andExpect(header().doesNotExist("ETag"))
            .andExpect(header().doesNotExist("Expires"));
  }

  @Test
  void largeIconAlreadyKnownIsNotModified() throws Exception {
    mockIcons(PNG, 42L);

    ResultActions response = mockMvc.perform(get(ICON_PATH.formatted("42")).header("If-None-Match", "\"900\""));

    response.andExpect(status().isNotModified());
  }

  @Test
  void svgIconKeepsItsTypeAndRunsNoScript() throws Exception {
    mockIcons(SVG, 42L);

    ResultActions response = mockMvc.perform(get(ICON_PATH.formatted("42")));

    response.andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/svg+xml"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"));
  }

  @Test
  void unversionedSmallIconIsCachedAMinuteAtMost() throws Exception {
    when(pwaManifestService.getSmallIcon("72x72")).thenReturn(new ManifestIcon(null, 12, PNG, 1L, 7L));

    ResultActions response = mockMvc.perform(get("/manifest/smallIcon?sizes=72x72"));

    response.andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", FALLBACK_CACHE))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().doesNotExist("ETag"))
            .andExpect(content().bytes(PNG));
  }

  @Test
  void monochromeIconIsNotFoundWithoutIcon() throws Exception {
    ResultActions response = mockMvc.perform(get("/manifest/monochromeIcon"));

    response.andExpect(status().isNotFound());
  }

  private void mockIcons(byte[] data, long sourceFileId) {
    // the thumbnail of a source file has a file id of its own
    when(pwaManifestService.getLargeIcon("512x512")).thenReturn(new ManifestIcon(null, data.length, data, 1L, 900L));
    when(pwaManifestService.getLargeIcon()).thenReturn(new ManifestIcon(null, data.length, data, 1L, sourceFileId));
  }

}
