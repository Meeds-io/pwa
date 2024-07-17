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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.portal.branding.model.BrandingFile;

import io.meeds.pwa.model.PwaManifestUpdate;
import io.meeds.pwa.service.PwaManifestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("manifest")
@Tag(name = "manifest", description = "Managing PWA Manifest")
public class PwaManifestRest {

  @Autowired
  private PwaManifestService pwaManifestService;

  @GetMapping
  @Operation(summary = "Get PWA Manifest file", description = "Get PWA Manifest file", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "PWA Manifest retrieved"),
                          @ApiResponse(responseCode = "304", description = "PWA Manifest not modified"),
  })
  public ResponseEntity<String> getManifest(
                                            WebRequest request,
                                            @Parameter(description = "The value of version parameter will determine whether the query should be cached by browser or not. If not set, no 'expires HTTP Header will be sent'")
                                            @RequestParam(name = "v", required = false)
                                            String version) {
    String eTag = String.valueOf(pwaManifestService.getManifestHash());
    if (request.checkNotModified(eTag)) {
      return null;
    }
    return ResponseEntity.ok()
                         .eTag(String.valueOf(eTag))
                         .lastModified(Instant.now())
                         .cacheControl(CacheControl.maxAge(Duration.ofDays(365)))
                         .body(pwaManifestService.getManifestContent());
  }

  @PutMapping
  @Secured("administrators")
  @Operation(summary = "Update Manifest information", description = "Update Manifest information", method = "PUT")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "204", description = "Manifest information updated"),
  })
  public void updateManifest(
                             @RequestBody
                             PwaManifestUpdate manifestUpdate) {
    pwaManifestService.updateManifest(manifestUpdate);
  }

  @GetMapping("/largeIcon")
  @Operation(summary = "Get PWA Manifest large icon file", description = "Get PWA Manifest large icon file", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
                          @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestLargeIcon(
                                                                  WebRequest request,
                                                                  @Parameter(description = "Dimensions of size")
                                                                  @RequestParam("sizes")
                                                                  String sizes) {
    InputStream inputStream = getBrandingFileResponse(request, pwaManifestService.getLargeIcon(sizes));
    return inputStream == null ? null :
                               ResponseEntity.ok()
                                             .contentType(MediaType.IMAGE_PNG)
                                             .body(new InputStreamResource(inputStream));
  }

  @GetMapping("/smallIcon")
  @Operation(summary = "Get PWA Manifest small icon file", description = "Get PWA Manifest small icon file", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
                          @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestSmallIcon(
                                                                  WebRequest request,
                                                                  @Parameter(description = "Dimensions of size")
                                                                  @RequestParam("sizes")
                                                                  String sizes,
                                                                  @Parameter(description = "The value of version parameter will determine whether the query should be cached by browser or not. If not set, no 'expires HTTP Header will be sent'")
                                                                  @RequestParam(name = "v", required = false)
                                                                  String version) {
    InputStream inputStream = getBrandingFileResponse(request, pwaManifestService.getSmallIcon(sizes));
    return inputStream == null ? null :
                               ResponseEntity.ok()
                                             .contentType(MediaType.IMAGE_PNG)
                                             .body(new InputStreamResource(inputStream));
  }

  private InputStream getBrandingFileResponse(WebRequest request,
                                              BrandingFile iconFile) {
    if (iconFile == null || iconFile.getData() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pwaManifest.imageFileNotFound");
    }
    long lastUpdated = iconFile.getUpdatedDate();
    String eTag = String.valueOf(lastUpdated);
    if (request.checkNotModified(eTag)) {
      return new ByteArrayInputStream(iconFile.getData());
    }
    return null;
  }

}
