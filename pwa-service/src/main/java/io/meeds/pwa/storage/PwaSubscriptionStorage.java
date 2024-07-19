/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2022 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.pwa.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;

import io.meeds.pwa.model.UserPushSubscription;
import io.meeds.social.util.JsonUtils;

@Component
public class PwaSubscriptionStorage {

  private static final Scope PWA_SUBSCRIPTION_SCOPE = Scope.APPLICATION.id("PWA_PUSH_SUBSCRIPTIONS");

  @Autowired
  private SettingService     settingService;

  public List<UserPushSubscription> get(String username) {
    Context context = Context.USER.id(username);
    @SuppressWarnings("rawtypes")
    Map<String, SettingValue> settings = settingService.getSettingsByContextAndScope(context.getName(),
                                                                                     context.getId(),
                                                                                     PWA_SUBSCRIPTION_SCOPE.getName(),
                                                                                     PWA_SUBSCRIPTION_SCOPE.getId());
    if (settings == null || settings.isEmpty()) {
      return Collections.emptyList();
    } else {
      return settings.values()
                     .stream()
                     .map(v -> v == null || v.getValue() == null ? null : v.getValue().toString())
                     .filter(Objects::nonNull)
                     .map(v -> JsonUtils.fromJsonString(v, UserPushSubscription.class))
                     .toList();
    }
  }

  public void create(UserPushSubscription subscription, String username) {
    settingService.set(Context.USER.id(username),
                       PWA_SUBSCRIPTION_SCOPE,
                       subscription.getId(),
                       SettingValue.create(JsonUtils.toJsonString(subscription)));
  }

  public void delete(String id, String username) {
    settingService.remove(Context.USER.id(username), PWA_SUBSCRIPTION_SCOPE, id);
  }

  public void deleteAll(String username) {
    settingService.remove(Context.USER.id(username), PWA_SUBSCRIPTION_SCOPE);
  }

}
