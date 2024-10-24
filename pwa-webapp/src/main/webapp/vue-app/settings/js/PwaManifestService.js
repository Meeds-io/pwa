/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

export function getManifest() {
  return fetch('/pwa/rest/manifest', {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp?.ok) {
      return resp.text().then(data => data && JSON.parse(data) || null);
    } else {
      throw new Error('Error while getting Manifest settings');
    }
  });
}

export function updateManifest(manifest) {
  return fetch('/pwa/rest/manifest', {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(manifest),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error saving Manifest settings');
    }
  });
}
