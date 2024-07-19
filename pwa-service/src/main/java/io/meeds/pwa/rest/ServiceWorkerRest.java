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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import io.meeds.pwa.service.PwaSwService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("service-worker")
@Tag(name = "service-worker", description = "Retrieve PWA Service Worker")
public class ServiceWorkerRest {

  @Autowired
  private PwaSwService pwaSwService;

  @GetMapping
  @Operation(summary = "Get PWA Service Worker", description = "Get PWA Service Worker", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "PWA Service Worker retrieved"),
                          @ApiResponse(responseCode = "304", description = "PWA Service Worker not modified"),
  })
  public ResponseEntity<String> getServiceWorkerContent(WebRequest request) {
    String content = pwaSwService.getContent();
    if (content == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    } else if (request.checkNotModified(String.valueOf(content.hashCode()))) {
      return null;
    } else {
      return ResponseEntity.ok()
                           .eTag(String.valueOf(content.hashCode()))
                           .header("Service-Worker-Allowed", "/")
                           .contentType(MediaType.valueOf("text/javascript"))
                           .body(content);
    }
  }
}
