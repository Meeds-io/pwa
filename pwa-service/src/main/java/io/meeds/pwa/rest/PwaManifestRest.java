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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.portal.branding.model.BrandingFile;

import io.meeds.portal.theme.util.BrandingImageValidator;

import io.meeds.pwa.model.ManifestIcon;
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
  public ResponseEntity<String> getManifest(WebRequest request,
                                            @Parameter(description = "The value of version parameter will determine whether the query should be cached by browser or not. If not set, no 'expires HTTP Header will be sent'")
                                            @RequestParam(name = "v", required = false)
                                            String version) {
    String eTag = String.valueOf(pwaManifestService.getManifestHash(request.getRemoteUser()));
    if (request.checkNotModified(eTag)) {
      return null;
    } else {
      return ResponseEntity.ok()
                           .eTag(String.valueOf(eTag))
                           .lastModified(Instant.now())
                           .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                           .body(pwaManifestService.getManifestContent(request.getRemoteUser()));
    }
  }

  @PutMapping
  @Secured("administrators")
  @Operation(summary = "Update Manifest information", description = "Update Manifest information", method = "PUT")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "204", description = "Manifest information updated"),
  })
  public void updateManifest(WebRequest request,
                             @RequestBody
                             PwaManifestUpdate manifestUpdate) {
    pwaManifestService.updateManifest(manifestUpdate, request.getRemoteUser());
  }

  @GetMapping("/largeIcon/{version}/{size}.png")
  @Operation(summary = "Get PWA Manifest large icon file", description = "Get PWA Manifest large icon file", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
                          @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestLargeIcon(WebRequest request,
                                                                  @Parameter(description = "Dimensions of size", required = true)
                                                                  @PathVariable("size") String size,
                                                                  @Parameter(description = "VersionId of the icon", required = true)
                                                                  @PathVariable("version") String version) {
    String realSize= size+"x"+size;
    return iconResponse(request, pwaManifestService.getLargeIcon(realSize), version, pwaManifestService.getLargeIcon());
  }

  @GetMapping("/smallIcon/{version}/{size}.png")
  @Operation(summary = "Get PWA Manifest small icon file", description = "Get PWA Manifest small icon file", method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
                          @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestSmallIcon(WebRequest request,
                                                                  @Parameter(description = "Dimensions of size", required = true)
                                                                  @PathVariable("size")
                                                                  String size,
                                                                  @Parameter(description = "VersionId of the icon", required = true)
                                                                  @PathVariable("version") String version) {
    String realSize= size+"x"+size;
    return iconResponse(request, pwaManifestService.getSmallIcon(realSize), version, pwaManifestService.getSmallIcon());
  }

  @GetMapping("/largeIcon")
  @Operation(summary = "Get PWA Manifest large icon file", description = "Get PWA Manifest large icon file", method = "GET")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
      @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestLargeIcon(WebRequest request,
                                                                  @Parameter(description = "Dimensions of size")
                                                                  @RequestParam(name = "sizes", required = false) String sizes) {
    return iconResponse(request, pwaManifestService.getLargeIcon(sizes), null, null);
  }

  @GetMapping("/monochromeIcon")
  @Operation(summary = "Get PWA Manifest monochrome icon file", description = "Get PWA Manifest monochrome icon file", method = "GET")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
      @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getMonochromeIcon(WebRequest request) {
    return iconResponse(request, pwaManifestService.getMonochromeIcon(), null, null);
  }

  @GetMapping("/smallIcon")
  @Operation(summary = "Get PWA Manifest small icon file", description = "Get PWA Manifest small icon file", method = "GET")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Icon file retrieved"),
      @ApiResponse(responseCode = "304", description = "Icon file not modified"),
  })
  public ResponseEntity<InputStreamResource> getManifestSmallIcon(WebRequest request,
                                                                  @Parameter(description = "Dimensions of size")
                                                                  @RequestParam(name = "sizes", required = false) String sizes) {
    return iconResponse(request, pwaManifestService.getSmallIcon(sizes), null, null);
  }

  /**
   * An icon is served with the type its bytes are, never sniffed by the
   * browser, and an SVG under a policy that runs no script. The URL of a
   * manifest carries the id of the file the icon comes from: asked with the
   * current id, the answer is cacheable for good, asked with another one (or
   * with none) it is the current icon, kept a minute at most and with no
   * validator.
   *
   * @param request the request
   * @param icon the icon to send, with its own size when a size was asked
   * @param version the version of the URL, null for an unversioned one
   * @param source the icon of the manifest the version designates, null for an
   *          unversioned URL
   * @return the answer, null when the request is answered not modified
   */
  private ResponseEntity<InputStreamResource> iconResponse(WebRequest request,
                                                           BrandingFile icon,
                                                           String version,
                                                           ManifestIcon source) {
    if (icon == null || icon.getData() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pwaManifest.imageFileNotFound");
    }
    String type = BrandingImageValidator.detectType(icon.getData());
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                                                       .contentType(type == null ? MediaType.IMAGE_PNG : MediaType.parseMediaType(type))
                                                       .header("X-Content-Type-Options", "nosniff");
    if (BrandingImageValidator.SVG.equals(type)) {
      builder.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
    }
    if (source != null && version != null && String.valueOf(source.getFileId()).equals(version)) {
      String eTag = String.valueOf(icon.getFileId());
      if (request.checkNotModified(eTag)) {
        return null;
      }
      return builder.eTag(eTag)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .header(HttpHeaders.EXPIRES,
                            DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusDays(365)))
                    .body(new InputStreamResource(new ByteArrayInputStream(icon.getData())));
    }
    return builder.cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                  .body(new InputStreamResource(new ByteArrayInputStream(icon.getData())));
  }

}
