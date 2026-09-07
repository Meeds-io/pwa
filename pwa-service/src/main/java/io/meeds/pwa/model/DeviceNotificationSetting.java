/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
package io.meeds.pwa.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-device setting of one direct-notification kind (e.g. "chat"), held on
 * the device's {@link UserPushSubscription}. An absent setting means the kind
 * is enabled with the caller's default delay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceNotificationSetting {

  private boolean enabled = true;

  /**
   * Delay in minutes a notification of this kind stays deferred on this
   * device; null or non-positive falls back to the caller's default.
   */
  private Integer delayMinutes;

}
