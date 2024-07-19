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

import org.exoplatform.portal.branding.model.Branding;

import lombok.Data;

@Data
public class PwaManifest {

  private static final String BRANDING_BASE_URL = "/portal/rest/v1/platform/branding/";

  private boolean             enabled;

  private String              name;

  private String              description;

  private String              largeIconPath;

  private String              smallIconPath;

  private String              backgroundColor;

  private String              themeColor;

  private String              version;

  private String              manifestId;

  private String              domainName;

  private String              descriptionKey;

  private String              content;

  private String              template;

  public String getContent(Branding branding) {
    if (enabled
        && template != null
        && content == null) {
      content = template.replace("$manifestId", manifestId)
                        .replace("$name", getName(branding))
                        .replace("$description", description)
                        .replace("$largeIconPath", getLargeIconPath(branding))
                        .replace("$smallIconPath", getSmallIconPath(branding))
                        .replace("$defaultLang", branding.getDefaultLanguage())
                        .replace("$dir", branding.getDirection())
                        .replace("$backgroundColor", getBackgroundColor(branding))
                        .replace("$domainName", domainName)
                        .replace("$themeColor", getThemeColor(branding));
    }
    return content;
  }

  private String getThemeColor(Branding branding) {
    return themeColor == null ? branding.getThemeStyle().get("primaryColor") : themeColor;
  }

  private String getBackgroundColor(Branding branding) {
    return backgroundColor == null ? branding.getThemeStyle().get("primaryColor") : backgroundColor;
  }

  private String getSmallIconPath(Branding branding) {
    return smallIconPath == null ? BRANDING_BASE_URL + "favicon?v=" + branding.getFavicon().getUpdatedDate() + "&size=16x16" :
                                 smallIconPath;
  }

  private String getLargeIconPath(Branding branding) {
    return largeIconPath == null ? BRANDING_BASE_URL + "logo?v=" + branding.getLogo().getUpdatedDate() + "&size=512x512" :
                                 largeIconPath;
  }

  private String getName(Branding branding) {
    return name == null ? branding.getCompanyName() : name;
  }

}
