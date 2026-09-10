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

/**
 * Contract of a deferred self-contained push scheduled through
 * {@code PwaNotificationService#scheduleDirectNotification}: the notification
 * content is produced at fire time, once per subscribed device.
 */
public interface PwaDirectNotificationBuilder {

  /**
   * Produces the notification to push to one device, or {@code null} to cancel
   * the send for that device — the caller's fire-time guard (e.g. "already
   * read", "already covered by this device's  popup") decides here, not at
   * schedule time. May be called concurrently for different devices. Each call
   * must return a fresh instance: the service mutates the returned message
   * while encoding it (e.g. shrinking the body to the payload cap).
   *
   * @param subscriptionId the id of the device subscription being fired
   * @return the notification to push, or null to cancel this device's send
   */
  PwaNotificationMessage build(String subscriptionId);

  /**
   * Called when the push could not be handed to the device's push service
   * (transport error, or a non-2xx status other than 410 Gone), so the caller
   * can make the content notifiable again for that device. Push stays a
   * best-effort channel: the default does nothing.
   *
   * @param subscriptionId the id of the device subscription that failed
   * @param message the message returned by {@link #build(String)}
   */
  default void onSendFailure(String subscriptionId, PwaNotificationMessage message) {
    // best-effort channel: nothing to do by default
  }
}
