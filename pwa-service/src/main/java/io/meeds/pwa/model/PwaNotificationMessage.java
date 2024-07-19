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
package io.meeds.pwa.model;

import java.util.List;

import lombok.Data;

@Data
public class PwaNotificationMessage {

  private String                      tag;

  private String                      image;

  private String                      lang;

  private String                      dir;

  private String                      icon;

  private String                      title;

  private String                      body;

  private String                      url;

  private List<Integer>               vibrate;

  private boolean                     renotify;

  private boolean                     requireInteraction;

  private boolean                     silent;

  private List<PwaNotificationAction> actions;

}
