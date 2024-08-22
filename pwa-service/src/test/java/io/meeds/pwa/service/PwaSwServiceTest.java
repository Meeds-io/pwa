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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import org.exoplatform.container.configuration.ConfigurationManager;

@SpringBootTest(classes = {
                            PwaSwService.class,
})
@TestPropertySource(
                    properties = {
                                   "pwa.service.worker.path=pwa/service-worker.js",
                    })
public class PwaSwServiceTest {

  @MockBean
  private ConfigurationManager configurationManager;

  @Autowired
  private PwaSwService         pwaSwService;

  @Test
  @SuppressWarnings("resource")
  public void getContent() throws Exception {
    assertNull(pwaSwService.getContent());

    when(configurationManager.getInputStream("pwa/service-worker.js")).thenAnswer(invocation -> getClass().getClassLoader()
                                                                                        .getResourceAsStream("pwa/service-worker.js"));
    assertNotNull(pwaSwService.getContent());
  }

}
