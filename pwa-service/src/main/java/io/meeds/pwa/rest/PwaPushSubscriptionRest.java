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

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.pwa.model.DeviceNotificationSetting;
import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.service.PwaSubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("subscriptions")
@Tag(name = "subscriptions", description = "Managing PWA Subscriptions")
public class PwaPushSubscriptionRest {

  private static final Pattern   MOBILE_PATTERN =
                                                Pattern.compile("(mobi|phone|blackberry|opera mini|fennec|minimo|symbian|psp|nintendo ds)",
                                                                Pattern.CASE_INSENSITIVE);

  private static final Pattern   TABLET_PATTERN = Pattern.compile("(tablet|ipad|playbook|silk)|(android(?!.*mobi))",
                                                                  Pattern.CASE_INSENSITIVE);

  @Autowired
  private PwaSubscriptionService pwaSubscriptionService;

  @PostMapping
  @Secured("users")
  @Operation(summary = "Create a new subscription",
             description = "This will save the newly Push Service subscription information to user settings",
             method = "POST")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "204", description = "Subscription created"),
  })
  public void subscribe(
                        WebRequest request,
                        @RequestBody
                        UserPushSubscription subscription) {
    subscription.setDeviceType(getDeviceType(request));
    pwaSubscriptionService.createSubscription(subscription,
                                              request.getRemoteUser());
  }

  @GetMapping("settings/{subscriptionId}/{notificationKind}")
  @Secured("users")
  @Operation(summary = "Retrieves one direct-notification kind's setting of one device subscription of the current user",
             description = "Returns the stored {enabled, delayMinutes} pair, or an empty body when the device follows the defaults",
             method = "GET")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Setting returned"),
                          @ApiResponse(responseCode = "404", description = "Subscription not found"),
  })
  public DeviceNotificationSetting getNotificationSetting(
                                                          WebRequest request,
                                                          @PathVariable("subscriptionId")
                                                          String subscriptionId,
                                                          @PathVariable("notificationKind")
                                                          String notificationKind) {
    try {
      return pwaSubscriptionService.getNotificationSetting(request.getRemoteUser(), subscriptionId, notificationKind);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PutMapping("settings/{subscriptionId}/{notificationKind}")
  @Secured("users")
  @Operation(summary = "Saves one direct-notification kind's setting on one device subscription of the current user",
             description = "Stores the {enabled, delayMinutes} pair for the given kind on the given device",
             method = "PUT")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "204", description = "Setting saved"),
                          @ApiResponse(responseCode = "400", description = "Invalid kind or delay"),
                          @ApiResponse(responseCode = "404", description = "Subscription not found"),
  })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void saveNotificationSetting(
                                      WebRequest request,
                                      @PathVariable("subscriptionId")
                                      String subscriptionId,
                                      @PathVariable("notificationKind")
                                      String notificationKind,
                                      @RequestBody
                                      DeviceNotificationSetting setting) {
    try {
      pwaSubscriptionService.saveNotificationSetting(request.getRemoteUser(), subscriptionId, notificationKind, setting);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @DeleteMapping
  @Secured("users")
  @Operation(summary = "Deletes an existing subscription",
             description = "This will delete a Push Service subscription information",
             method = "DELETE")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "204", description = "Subscription deleted"),
  })
  public void unsubscribe(
                          WebRequest request,
                          @RequestBody
                          UserPushSubscription subscription) {
    subscription.setDeviceType(getDeviceType(request));
    pwaSubscriptionService.deleteSubscription(subscription.getId(),
                                              request.getRemoteUser());
  }

  public String getDeviceType(WebRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (StringUtils.isNotBlank(userAgent)) {
      if (isTablet(userAgent)) {
        return "Tablet";
      } else if (isMobile(userAgent)) {
        return "Mobile";
      } else {
        return "Desktop";
      }
    } else {
      return "Robot";
    }
  }

  private boolean isTablet(String userAgent) {
    return TABLET_PATTERN.matcher(userAgent).find();
  }

  private boolean isMobile(String userAgent) {
    return MOBILE_PATTERN.matcher(userAgent).find();
  }

}
