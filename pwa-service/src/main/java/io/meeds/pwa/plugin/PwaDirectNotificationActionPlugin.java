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
package io.meeds.pwa.plugin;

import java.util.Map;

import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Handles the quick actions a user triggers on a direct (self-contained) push
 * notification of one kind (e.g. "chat"). The pwa layer authenticates the
 * device (token + HMAC proof) and resolves the user before dispatching; the
 * plugin only carries the domain behavior of the action.
 */
public interface PwaDirectNotificationActionPlugin {

  /**
   * @return the direct-notification kind this plugin handles, the same key
   *         used when scheduling ({@code scheduleDirectNotification})
   */
  String getNotificationKind();

  /**
   * @param username authenticated owner of the device that triggered the action
   * @param action the action id carried by the notification (e.g. "markRead")
   * @param objectKey the object the device's token was scoped to (the
   *          notification tag at scheduling time) — the
   *          only trusted target of the action
   * @param data the notification's routing data as echoed by the device
   *          (untrusted beyond {@code objectKey})
   * @throws ObjectNotFoundException when the target object no longer exists
   * @throws IllegalAccessException when the user may not act on the object
   * @throws IllegalArgumentException when the action or data is invalid
   */
  void handleAction(String username, String action, String objectKey, Map<String, String> data) throws ObjectNotFoundException,
                                                                                               IllegalAccessException;

}
