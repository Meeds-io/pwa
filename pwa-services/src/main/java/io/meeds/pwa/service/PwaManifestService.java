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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.pwa.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.api.settings.ExoFeatureService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.branding.model.BrandingFile;
import org.exoplatform.portal.branding.model.Favicon;
import org.exoplatform.portal.branding.model.Logo;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.thumbnail.ImageResizeService;
import org.exoplatform.services.thumbnail.ImageThumbnailService;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

import io.meeds.common.ContainerTransactional;
import io.meeds.pwa.model.ManifestIcon;
import io.meeds.pwa.model.PwaManifest;
import io.meeds.pwa.model.PwaManifestUpdate;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Service
public class PwaManifestService {

  private static final Log      LOG                      = ExoLogger.getExoLogger(PwaManifestService.class);

  private static final String   RESET_ATTACHMENT_ID      = "0";

  private static final String   PWA_LARGE_ICON_BASE_PATH = "/portal/rest/v1/platform/branding/manifest/largeIcon?v="; // NOSONAR

  private static final String   PWA_SMALL_ICON_BASE_PATH = "/portal/rest/v1/platform/branding/manifest/smallIcon?v="; // NOSONAR

  private static final String   FILE_API_NAME_SPACE      = "CompanyBranding";

  private static final String   PWA_LARGE_ICON_NAME      = "largeIcon.png";

  private static final String   PWA_SMALL_ICON_NAME      = "smallIcon.png";

  private static final String   PWA_FEATURE              = "pwa";

  private static final String   PWA_NAME                 = "pwa.name";

  private static final String   PWA_DESCRIPTION          = "pwa.description";

  private static final String   PWA_BACKGROUND_COLOR     = "pwa.backgroundColor";

  private static final String   PWA_THEME_COLOR          = "pwa.themeColor";

  private static final String   PWA_LARGE_ICON           = "pwa.illustration512";

  private static final String   PWA_SMALL_ICON           = "pwa.illustration71";

  private static final boolean  DEVELOPPING              = PropertyManager.isDevelopping();

  private static final String   DEFAULT_DOMAIN_NAME      = "test.meeds.io";

  private static final String   DOMAIN_URL_PARAM_NAME    = "gatein.email.domain.url";

  @Autowired
  private SettingService        settingService;

  @Autowired
  private FileService           fileService;

  @Autowired
  private UploadService         uploadService;

  @Autowired
  private BrandingService       brandingService;

  @Autowired
  private ConfigurationManager  configurationManager;

  @Autowired
  private ExoFeatureService     featureService;

  @Autowired
  private ResourceBundleService resourceBundleService;

  @Autowired
  private ImageThumbnailService imageThumbnailService;

  @Autowired
  private ImageResizeService    imageResizeService;

  @Autowired
  private ListenerService       listenerService;

  @Value("${pwa.manifest.id:}")
  private String                manifestId;

  @Value("${pwa.manifest.version:}")
  private String                manifestVersion;

  @Value("${pwa.manifest.description:}")
  private String                manifestDescriptionKey;

  @Value("${pwa.manifest.path:jar:/pwa/manifest.json}")
  private String                pwaManifestPath;

  private PwaManifest           pwaManifest              = new PwaManifest();

  private ManifestIcon          largeIcon                = null;

  private ManifestIcon          smallIcon                = null;

  @PostConstruct
  @ContainerTransactional
  public void init() {
    computePwaProperties();
    listenerService.addListener(BrandingService.BRANDING_UPDATED_EVENT, event -> this.computePwaProperties());
  }

  public boolean isPwaEnabled() {
    return pwaManifest.isEnabled();
  }

  public long getManifestHash() {
    return Objects.hash(brandingService.getLastUpdatedTime(), Objects.hash(getManifestContent()));
  }

  public String getManifestContent() {
    if (pwaManifest.getContent() == null) {
      computePwaProperties();
      return pwaManifest.getContent(brandingService.getBrandingInformation(false));
    } else {
      return pwaManifest.getContent();
    }
  }

  public String getThemeColor() {
    return pwaManifest.isEnabled() && pwaManifest.getThemeColor() != null ? pwaManifest.getThemeColor() :
                                                                          brandingService.getThemeStyle().get("primaryColor");
  }

  public void updateManifest(PwaManifestUpdate manifest) {
    validateCSSInputs(manifest);
    try {
      featureService.saveActiveFeature(PWA_FEATURE, manifest.isEnabled());
      if (manifest.isEnabled()) {
        updatePropertyValue(PWA_NAME, manifest.getName(), false);
        updatePropertyValue(PWA_DESCRIPTION, manifest.getDescription(), false);
        updatePropertyValue(PWA_BACKGROUND_COLOR, manifest.getBackgroundColor(), false);
        updatePropertyValue(PWA_THEME_COLOR, manifest.getThemeColor(), false);
        updateBrandingFile(manifest.getLargeIconUploadId(),
                           PWA_LARGE_ICON_NAME,
                           getLargeIcon().getFileId(),
                           PWA_LARGE_ICON);
        updateBrandingFile(manifest.getSmallIconUploadId(),
                           PWA_SMALL_ICON_NAME,
                           getSmallIcon().getFileId(),
                           PWA_SMALL_ICON);
      }
    } finally {
      this.pwaManifest.setContent(null);
      this.largeIcon = null;
      this.smallIcon = null;
    }
  }

  public ManifestIcon getLargeIcon() {
    if (this.largeIcon == null) {
      try {
        Long imageId = getPropertyValueLong(PWA_LARGE_ICON);
        if (imageId != null) {
          this.largeIcon = retrieveStoredBrandingFile(imageId, new ManifestIcon());
        } else {
          Logo brandingLogo = brandingService.getLogo();
          this.largeIcon = new ManifestIcon(null,
                                            brandingLogo.getSize(),
                                            brandingLogo.getData(),
                                            brandingLogo.getUpdatedDate(),
                                            brandingLogo.getFileId());
        }
      } catch (Exception e) {
        LOG.warn("Error retrieving manifest large icon", e);
      }
    }
    return this.largeIcon;
  }

  @SneakyThrows
  public ManifestIcon getLargeIcon(String dimensions) {
    ManifestIcon manifestLargeIcon = getLargeIcon();
    return getBrandingFileThumbnail(manifestLargeIcon, dimensions);
  }

  public ManifestIcon getSmallIcon() {
    if (this.smallIcon == null) {
      try {
        Long imageId = getPropertyValueLong(PWA_SMALL_ICON);
        if (imageId != null) {
          this.smallIcon = retrieveStoredBrandingFile(imageId, new ManifestIcon());
        } else {
          Favicon brandingFavicon = brandingService.getFavicon();
          this.smallIcon = new ManifestIcon(null,
                                            brandingFavicon.getSize(),
                                            brandingFavicon.getData(),
                                            brandingFavicon.getUpdatedDate(),
                                            brandingFavicon.getFileId());
        }
      } catch (Exception e) {
        LOG.warn("Error retrieving manifest small icon", e);
      }
    }
    return this.smallIcon;
  }

  @SneakyThrows
  public ManifestIcon getSmallIcon(String dimensions) {
    ManifestIcon brandingFile = getSmallIcon();
    return getBrandingFileThumbnail(brandingFile, dimensions);
  }

  public String getLargeIconPath() {
    ManifestIcon manifestIcon = getLargeIcon();
    return PWA_LARGE_ICON_BASE_PATH + Objects.hash(manifestIcon.getUpdatedDate());
  }

  public String getSmallIconPath() {
    ManifestIcon manifestIcon = getSmallIcon();
    return PWA_SMALL_ICON_BASE_PATH + Objects.hash(manifestIcon.getUpdatedDate());
  }

  private void updateBrandingFile(String uploadId, String fileName, Long fileId, String settingKey) {
    try {
      if (StringUtils.equals(RESET_ATTACHMENT_ID, uploadId)) {
        removeBrandingFile(fileId, settingKey);
      } else if (StringUtils.isNotBlank(uploadId)) {
        updateBrandingFileByUploadId(uploadId, fileName, settingKey);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error while updating login background", e);
    }
  }

  private void removeBrandingFile(Long fileId, String settingKey) {
    if (fileId != null && fileId > 0) {
      fileService.deleteFile(fileId);
      settingService.remove(Context.GLOBAL,
                            Scope.GLOBAL,
                            settingKey);
    }
  }

  private void updateBrandingFileByUploadId(String uploadId,
                                            String fileName,
                                            String settingKey) throws Exception {
    InputStream inputStream = getUploadDataAsStream(uploadId);
    if (inputStream == null) {
      throw new IllegalArgumentException("Cannot update " + fileName +
          ", the object must contain the image data or an upload id");
    }
    int size = inputStream.available();
    FileItem fileItem = new FileItem(0l,
                                     fileName,
                                     "image/png",
                                     FILE_API_NAME_SPACE,
                                     size,
                                     new Date(),
                                     getCurrentUserId(),
                                     false,
                                     inputStream);
    fileItem = fileService.writeFile(fileItem);
    settingService.set(Context.GLOBAL,
                       Scope.GLOBAL,
                       settingKey,
                       SettingValue.create(String.valueOf(fileItem.getFileInfo().getId())));
  }

  @SneakyThrows
  private void computePwaProperties() {
    pwaManifest.setEnabled(featureService.isActiveFeature(PWA_FEATURE));
    pwaManifest.setManifestId(getDomainName());
    if (pwaManifestPath != null && pwaManifest.getTemplate() == null || DEVELOPPING) {
      try (InputStream inputStream = configurationManager.getInputStream(pwaManifestPath)) {
        pwaManifest.setTemplate(IOUtil.getStreamContentAsString(inputStream));
      }
    }
    pwaManifest.setName(getPropertyValue(PWA_NAME));
    pwaManifest.setManifestId(manifestId);
    pwaManifest.setVersion(manifestVersion);
    pwaManifest.setDescriptionKey(manifestDescriptionKey);
    pwaManifest.setDescription(getPropertyValue(PWA_DESCRIPTION));
    computePwaDescription();

    pwaManifest.setBackgroundColor(getPropertyValue(PWA_BACKGROUND_COLOR));

    pwaManifest.setThemeColor(getPropertyValue(PWA_THEME_COLOR));

    largeIcon = null;
    pwaManifest.setLargeIconPath(getLargeIconPath());

    smallIcon = null;
    pwaManifest.setSmallIconPath(getSmallIconPath());
  }

  private void computePwaDescription() {
    if (pwaManifest.getDescription() == null) {
      Locale defaultLocale = Locale.forLanguageTag(brandingService.getDefaultLanguage());
      String[] sharedResourceBundleNames = resourceBundleService.getSharedResourceBundleNames();
      ResourceBundle resourceBundle = resourceBundleService.getResourceBundle(sharedResourceBundleNames,
                                                                              defaultLocale);
      if (resourceBundle.containsKey(pwaManifest.getDescriptionKey())) {
        pwaManifest.setDescription(resourceBundle.getString(pwaManifest.getDescriptionKey()));
      } else if (!Locale.ENGLISH.equals(defaultLocale)) {
        resourceBundle = resourceBundleService.getResourceBundle(sharedResourceBundleNames,
                                                                 Locale.ENGLISH);
        if (resourceBundle.containsKey(pwaManifest.getDescriptionKey())) {
          pwaManifest.setDescription(resourceBundle.getString(pwaManifest.getDescriptionKey()));
        }
      }
    }
  }

  private String getDomainName() {
    String domain = System.getProperty(DOMAIN_URL_PARAM_NAME);
    if (StringUtils.isBlank(domain)) {
      return DEFAULT_DOMAIN_NAME;
    } else {
      return domain.replace("https://", "")
                   .replace("http://", "")
                   .replaceAll("(:\\d*)?/?", "");
    }
  }

  private InputStream getUploadDataAsStream(String uploadId) throws FileNotFoundException {
    UploadResource uploadResource = uploadService.getUploadResource(uploadId);
    if (uploadResource == null) {
      return null;
    } else {
      try {// NOSONAR
        return new FileInputStream(new File(uploadResource.getStoreLocation()));
      } finally {
        uploadService.removeUploadResource(uploadId);
      }
    }
  }

  private String getCurrentUserId() {
    ConversationState conversationState = ConversationState.getCurrent();
    if (conversationState != null && conversationState.getIdentity() != null) {
      return conversationState.getIdentity().getUserId();
    }
    return null;
  }

  private <T extends BrandingFile> T retrieveStoredBrandingFile(long imageId, T brandingFile) throws FileStorageException {
    FileItem fileItem = fileService.getFile(imageId);
    if (fileItem != null) {
      brandingFile.setData(fileItem.getAsByte());
      brandingFile.setSize(fileItem.getFileInfo().getSize());
      brandingFile.setUpdatedDate(fileItem.getFileInfo().getUpdatedDate().getTime());
      brandingFile.setFileId(imageId);
    }
    return brandingFile;
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private <T extends BrandingFile> T getBrandingFileThumbnail(BrandingFile brandingFile,
                                                              String dimensions) throws FileStorageException,
                                                                                 IOException {
    if (StringUtils.isBlank(dimensions)
        || !StringUtils.contains(dimensions, "x")) {
      return (T) brandingFile;
    } else {
      brandingFile = brandingFile.cloneFile();
      FileItem fileItem = fileService.getFile(brandingFile.getFileId());

      Integer[] dimensionParts = Arrays.stream(dimensions.split("x")).map(Integer::parseInt).toArray(Integer[]::new);
      FileItem thumbnailFileItem = imageThumbnailService.getOrCreateThumbnail(this::scaleImage,
                                                                              fileItem,
                                                                              dimensionParts[0],
                                                                              dimensionParts[1]);
      brandingFile.setFileId(thumbnailFileItem.getFileInfo().getId());
      try (InputStream inputStream = thumbnailFileItem.getAsStream()) {
        brandingFile.setData(IOUtils.toByteArray(inputStream));
      }
      return (T) brandingFile;
    }
  }

  private byte[] scaleImage(byte[] image, int width, int height, boolean fitExact, boolean ultraQuality) throws Exception {
    return imageResizeService.scaleImage(image, width, height, true, true);
  }

  private String getPropertyValue(String key) {
    return getPropertyValue(key, null);
  }

  private Long getPropertyValueLong(String key) {
    String value = getPropertyValue(key, null);
    return StringUtils.isBlank(value) ? null : Long.parseLong(value);
  }

  private String getPropertyValue(String key, String defaultValue) {
    SettingValue<?> value = settingService.get(Context.GLOBAL,
                                               Scope.GLOBAL,
                                               key);
    if (value != null
        && value.getValue() != null
        && StringUtils.isNotBlank(value.getValue().toString())) {
      return value.getValue().toString();
    } else {
      return defaultValue;
    }
  }

  private void updatePropertyValue(String key, String value, boolean updateLastUpdatedTime) {
    if (StringUtils.isBlank(value)) {
      settingService.remove(Context.GLOBAL, Scope.GLOBAL, key);
    } else {
      settingService.set(Context.GLOBAL, Scope.GLOBAL, key, SettingValue.create(value));
    }
    if (updateLastUpdatedTime) {
      refreshManifest();
    }
  }

  private void validateCSSInputs(PwaManifestUpdate manifest) {
    Arrays.asList(manifest.getBackgroundColor(),
                  manifest.getThemeColor())
          .forEach(this::validateCSSStyleValue);
  }

  private void validateCSSStyleValue(String value) {
    if (StringUtils.isNotBlank(value)
        && (value.contains("javascript") || value.contains("eval"))) {
      throw new IllegalArgumentException(String.format("Invalid css value input %s",
                                                       value));
    }
  }

  private void refreshManifest() {
    pwaManifest.setContent(null);
  }

}
