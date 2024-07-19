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
package io.meeds.pwa.plugin;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.WebNotificationService;

import io.meeds.pwa.model.PwaNotificationMessage;

@FunctionalInterface
public interface PwaNotificationPlugin {

  /**
   * Converts a Web Notification into a Push Notification
   * 
   * @param notification {@link NotificationInfo} retrieved from
   *          {@link WebNotificationService} data model
   * @return {@link PwaNotificationMessage} containing title, body and Web
   *         Notification elements to display to end user
   */
  PwaNotificationMessage process(NotificationInfo notification);

}
