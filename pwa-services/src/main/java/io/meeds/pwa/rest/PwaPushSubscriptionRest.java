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
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.pwa.service.PwaSubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("subscriptions")
@Tag(name = "subscriptions", description = "Managing PWA Subscriptions")
public class PwaPushSubscriptionRest {

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
                        HttpServletRequest request,
                        @RequestBody
                        UserPushSubscription subscription) {
    pwaSubscriptionService.createSubscription(subscription, request.getRemoteUser());
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
                          HttpServletRequest request,
                          @RequestBody
                          UserPushSubscription subscription) {
    pwaSubscriptionService.deleteSubscription(subscription.getEndpoint(), request.getRemoteUser());
  }

}
