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

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import lombok.Data;

@Data
public class UserPushSubscription {

  private String id;

  private String auth;

  private String key;

  private String endpoint;

  private String deviceType;

  private String pushDeviceSecret;

  /**
   * Per-device settings of direct-notification kinds (e.g. "chat"), keyed by
   * kind. Absent entry = enabled with the caller's default delay. Persisted
   * with the subscription JSON; a value sent by the client subscribe call is
   * discarded (see PwaSubscriptionService#createSubscription).
   */
  private Map<String, DeviceNotificationSetting> notificationSettings;

  public DeviceNotificationSetting getNotificationSetting(String notificationKind) {
    return notificationSettings == null || notificationKind == null ? null : notificationSettings.get(notificationKind);
  }

  public void setNotificationSetting(String notificationKind, DeviceNotificationSetting setting) {
    if (notificationSettings == null) {
      notificationSettings = new HashMap<>();
    }
    notificationSettings.put(notificationKind, setting);
  }

  public byte[] authAsBytes() {
    return Base64.getDecoder().decode(getAuth());
  }

  public byte[] keyAsBytes() {
    return Base64.getDecoder().decode(getKey());
  }

  public PublicKey userPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
    KeyFactory kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
    ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
    ECPoint point = ecSpec.getCurve().decodePoint(keyAsBytes());
    ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);
    return kf.generatePublic(pubSpec);
  }

}
