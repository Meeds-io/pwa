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

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("notifications")
@Tag(name = "notifications", description = "Managing PWA Notifications")
public class PwaNotificationRest {

  @Autowired
  private PwaNotificationService pwaNotificationService;

  @GetMapping("{id}")
  @Secured("users")
  @Operation(summary = "Get Web Notification", description = "Get Web Notification", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Web Notification retrieved"),
  })
  public PwaNotificationMessage getNotification(
                                                HttpServletRequest request,
                                                @Parameter(description = "The Web Notification Identifier")
                                                @PathVariable("id")
                                                long notificationId) {
    try {
      return pwaNotificationService.getNotification(notificationId, request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

  @GetMapping("{id}/push")
  @Operation(summary = "Get Web Notification from push token", description = "Get Web Notification from push token", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Web Notification retrieved"),
  })
  public PwaNotificationMessage getNotificationFromPush(HttpServletRequest request,
                                                        @Parameter(description = "The Web Notification Identifier")
                                                        @PathVariable("id")
                                                        long notificationId,
                                                        @RequestHeader(value = "Authorization", required = false)
                                                        String authorizationHeader) {
    try {
      return pwaNotificationService.getNotificationFromPush(notificationId,
                                                            authorizationHeader,
                                                            request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

  @PatchMapping(path = "{id}/push", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Operation(summary = "Update Web Notification from push token", description = "Update Web Notification from push token", method = "PATCH")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Web Notification updated"),
  })
  public void updateNotificationPropertyFromPush(HttpServletRequest request,
                                                 @Parameter(description = "The Web Notification Identifier")
                                                 @PathVariable("id")
                                                 long notificationId,
                                                 @Parameter(description = "The Web Notification action")
                                                 @RequestParam("action")
                                                 String action,
                                                 @RequestHeader(value = "Authorization", required = false)
                                                 String authorizationHeader) {
    try {
      pwaNotificationService.updateNotificationFromPush(notificationId,
                                                        action,
                                                        authorizationHeader,
                                                        request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

  @PostMapping(path = "{id}/push/delivery-delay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Operation(summary = "Report delayed push notification delivery", description = "Report delayed push notification delivery", method = "POST")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Push delivery delay reported"),
  })
  public void reportPushDeliveryDelay(HttpServletRequest request,
                                      @Parameter(description = "The Web Notification Identifier")
                                      @PathVariable("id")
                                      long notificationId,
                                      @RequestParam("sentAt")
                                      long sentAt,
                                      @RequestParam("receivedAt")
                                      long receivedAt,
                                      @RequestHeader(value = "Authorization", required = false)
                                      String authorizationHeader) {
    try {
      pwaNotificationService.reportPushDeliveryDelay(notificationId,
                                                     authorizationHeader,
                                                     request.getRemoteUser(),
                                                     sentAt,
                                                     receivedAt);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

  @GetMapping("push/delivery-delay/{subscriptionId}")
  @Secured("users")
  @Operation(summary = "Get delayed push notification delivery status", description = "Get delayed push notification delivery status", method = "GET")
  public Map<String, Object> getPushDeliveryDelayStatus(HttpServletRequest request,
                                                        @Parameter(description = "The Push Subscription Id")
                                                        @PathVariable("subscriptionId")
                                                        String subscriptionId) {
    return pwaNotificationService.getPushDeliveryDelayStatus(request.getRemoteUser(), subscriptionId);
  }

  @DeleteMapping("push/delivery-delay/{subscriptionId}")
  @Secured("users")
  @Operation(summary = "Reset delayed push notification delivery status", description = "Reset delayed push notification delivery status", method = "DELETE")
  public void resetPushDeliveryDelayStatus(HttpServletRequest request,
                                           @Parameter(description = "The Push Subscription Id")
                                           @PathVariable("subscriptionId")
                                           String subscriptionId) {
    pwaNotificationService.resetPushDeliveryDelay(request.getRemoteUser(), subscriptionId);
  }

  @GetMapping("badge")
  @Secured("users")
  @Operation(summary = "Get PWA badge count", description = "Get the current PWA application badge count", method = "GET")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "PWA badge count retrieved"),
  })
  public Map<String, Object> getBadge(HttpServletRequest request) {
    return pwaNotificationService.getBadge(request.getRemoteUser());
  }

  @PatchMapping(path = "{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Secured("users")
  @Operation(summary = "Update Web Notification specific property", description = "Update Web Notification specific property", method = "PATCH")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Web Notification updated"),
  })
  public void updateNotificationProperty(
                                         HttpServletRequest request,
                                         @Parameter(description = "The Web Notification Identifier")
                                         @PathVariable("id")
                                         long notificationId,
                                         @Parameter(description = "The Web Notification Identifier")
                                         @RequestParam("action")
                                         String action) {
    try {
      pwaNotificationService.updateNotification(notificationId, action, request.getRemoteUser());
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

}
