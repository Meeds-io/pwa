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

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.security.codec.CodecInitializer;

import io.meeds.common.ContainerTransactional;
import io.meeds.pwa.utils.VapidKeysUtils;
import io.meeds.social.util.JsonUtils;

import lombok.SneakyThrows;

@Component
public class PwaNotificationStorage {

  private static final Log     LOG                               = ExoLogger.getLogger(PwaNotificationStorage.class);

  private static final Context PWA_CONTEXT                       = Context.GLOBAL.id("PWA");

  private static final Scope   PWA_VAPID_KEYS_SCOPE              = Scope.APPLICATION.id("PWA_VAPID_KEYS");

  private static final Scope   PWA_PUSH_DELIVERY_DELAY_SCOPE     = Scope.APPLICATION.id("PWA_PUSH_DELIVERY_DELAY");

  private static final String  PWA_PUSH_EXCESSIVE_DELIVERY_DELAY = "PWA_PUSH_EXCESSIVE_DELIVERY_DELAY-%s";

  private static final String  PWA_VAPID_PUBLIC_KEY              = "VAPID_PUBLIC_KEY";

  private static final String  PWA_VAPID_PRIVATE_KEY             = "VAPID_PRIVATE_KEY";

  @Autowired
  private SettingService       settingService;

  @Autowired
  private CodecInitializer     codecInitializer;

  @Value("${pwa.notifications.enabled:true}")
  private boolean              enabled;

  public PublicKey getVapidPublicKey() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    String vapidPublicKeyString = getVapidPublicKeyString();
    if (StringUtils.isBlank(vapidPublicKeyString)) {
      this.generateVapidKeys();
      vapidPublicKeyString = getVapidPublicKeyString();
    }
    return VapidKeysUtils.decodePublicKey(vapidPublicKeyString);
  }

  public PrivateKey getVapidPrivateKey() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    String vapidPrivateKeyString = getVapidPrivateKeyString();
    if (StringUtils.isBlank(vapidPrivateKeyString)) {
      this.generateVapidKeys();
      vapidPrivateKeyString = getVapidPrivateKeyString();
    }
    return VapidKeysUtils.decodePrivateKey(vapidPrivateKeyString);
  }

  public String getVapidPublicKeyString() {
    return getValue(PWA_VAPID_KEYS_SCOPE, PWA_VAPID_PUBLIC_KEY);
  }

  public void recordExcessivePushDeliveryDelay(String username, String subscriptionId, long delayMs) {
    Map<String, Object> params = Map.of("subscriptionId",
                                        subscriptionId,
                                        "detectedAt",
                                        System.currentTimeMillis(),
                                        "delayMs",
                                        delayMs);
    setValue(Context.USER.id(username),
             PWA_PUSH_DELIVERY_DELAY_SCOPE,
             PWA_PUSH_EXCESSIVE_DELIVERY_DELAY.formatted(subscriptionId),
             JsonUtils.toJsonString(params));
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getPushDeliveryDelayStatus(String username, String subscriptionId, long maxAgeMillis) {
    String paramsString = getValue(Context.USER.id(username),
                                   PWA_PUSH_DELIVERY_DELAY_SCOPE,
                                   PWA_PUSH_EXCESSIVE_DELIVERY_DELAY.formatted(subscriptionId));
    if (StringUtils.isBlank(paramsString)) {
      return Collections.emptyMap();
    } else {
      Map<String, Object> params = (Map<String, Object>) JsonUtils.fromJsonString(paramsString, Map.class);// NOSONAR
      long detectedAt = Long.parseLong(params.get("detectedAt").toString());
      if (System.currentTimeMillis() - detectedAt > maxAgeMillis) {
        resetPushDeliveryDelay(username, subscriptionId);
        return Collections.emptyMap();
      } else {
        return params;
      }
    }
  }

  public void resetPushDeliveryDelay(String username, String subscriptionId) {
    removeValue(Context.USER.id(username),
                PWA_PUSH_DELIVERY_DELAY_SCOPE,
                PWA_PUSH_EXCESSIVE_DELIVERY_DELAY.formatted(subscriptionId));
  }

  @ContainerTransactional
  protected void generateVapidKeys() {
    if (!this.enabled) {
      return;
    }
    String publicKey = getVapidPublicKeyString();
    if (StringUtils.isBlank(publicKey)) {
      try {
        KeyPair keyPair = VapidKeysUtils.generateKeys();
        setVapidPublicKeyString(VapidKeysUtils.encode((ECPublicKey) keyPair.getPublic()));
        setVapidPrivateKeyString(VapidKeysUtils.encode((ECPrivateKey) keyPair.getPrivate()));
      } catch (Exception e) {
        LOG.warn("Error while generating keys for Push Notifications, it will be disabled until resolving the error", e);
        this.enabled = false;
      }
    }
  }

  private String getVapidPrivateKeyString() {
    return decrypt(getValue(PWA_VAPID_KEYS_SCOPE, PWA_VAPID_PRIVATE_KEY));
  }

  private void setVapidPublicKeyString(String publicKey) {
    setValue(PWA_VAPID_KEYS_SCOPE, PWA_VAPID_PUBLIC_KEY, publicKey);
  }

  private void setVapidPrivateKeyString(String privateKey) {
    setValue(PWA_VAPID_KEYS_SCOPE, PWA_VAPID_PRIVATE_KEY, crypt(privateKey));
  }

  private String getValue(Scope scope, String key) {
    return getValue(PWA_CONTEXT, scope, key);
  }

  private String getValue(Context context, Scope scope, String key) {
    SettingValue<?> settingValue = settingService.get(context, scope, key);
    return settingValue == null || settingValue.getValue() == null ? null : settingValue.getValue().toString();
  }

  private void setValue(Scope scope, String key, String value) {
    setValue(PWA_CONTEXT, scope, key, value);
  }

  private void setValue(Context context, Scope scope, String key, String value) {
    settingService.set(context, scope, key, SettingValue.create(value));
  }

  private void removeValue(Context context, Scope scope, String key) {
    settingService.remove(context, scope, key);
  }

  @SneakyThrows
  private String crypt(String value) {
    return codecInitializer.getCodec().encode(value);
  }

  @SneakyThrows
  private String decrypt(String value) {
    if (value == null) {
      return null;
    } else {
      return codecInitializer.getCodec().decode(value);
    }
  }

}
