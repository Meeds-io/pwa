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
package io.meeds.pwa.service;

import static io.meeds.pwa.service.PwaManifestService.DEPRECATED_PUSH_CHANNEL_ID;
import static io.meeds.pwa.service.PwaManifestService.PWA_BACKGROUND_COLOR;
import static io.meeds.pwa.service.PwaManifestService.PWA_DESCRIPTION;
import static io.meeds.pwa.service.PwaManifestService.PWA_FEATURE;
import static io.meeds.pwa.service.PwaManifestService.PWA_LARGE_ICON;
import static io.meeds.pwa.service.PwaManifestService.PWA_LARGE_ICON_BASE_PATH;
import static io.meeds.pwa.service.PwaManifestService.PWA_NAME;
import static io.meeds.pwa.service.PwaManifestService.PWA_SMALL_ICON;
import static io.meeds.pwa.service.PwaManifestService.PWA_SMALL_ICON_BASE_PATH;
import static io.meeds.pwa.service.PwaManifestService.PWA_THEME_COLOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import org.exoplatform.commons.api.notification.channel.AbstractChannel;
import org.exoplatform.commons.api.notification.channel.ChannelManager;
import org.exoplatform.commons.api.notification.model.ChannelKey;
import org.exoplatform.commons.api.settings.ExoFeatureService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.branding.model.Branding;
import org.exoplatform.portal.branding.model.Favicon;
import org.exoplatform.portal.branding.model.Logo;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.thumbnail.ImageResizeService;
import org.exoplatform.services.thumbnail.ImageThumbnailService;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

import io.meeds.pwa.model.ManifestIcon;
import io.meeds.pwa.model.PwaManifest;
import io.meeds.pwa.model.PwaManifestUpdate;

@SpringBootTest(classes = {
                            PwaManifestService.class,
})
@TestPropertySource(
                    properties = {
                                   "pwa.manifest.id=manifestId",
                                   "pwa.manifest.version=V4",
                                   "pwa.manifest.description=manifestDescriptionKey",
                                   "pwa.manifest.path=pwaManifestPath",
                    })
@ExtendWith(MockitoExtension.class)
public class PwaManifestServiceTest {

  private static final Random   RANDOM                   = new Random();

  private static final String   TEST_USER                = "testUser";

  private static final String   PRIMARY_COLOR            = "defaultPrimaryColor";

  private static final String   LARGE_ICON_FILE_ID       = "15";

  private static final String   SMALL_ICON_FILE_ID       = "12";

  private static final String   MANIFEST_DESCRIPTION     = "manifestDescription";

  private static final String   THEME_COLOR              = "themeColor";

  private static final String   BACKGROUND_COLOR         = "backgroundColor";

  private static final String   MANIFEST_NAME            = "manifestName";

  private static final String   MANIFEST_ID              = "manifestId";

  private static final String   MANIFEST_VERSION         = "V4";

  private static final String   MANIFEST_DESCRIPTION_KEY = "manifestDescriptionKey";

  private static final String   PWA_MANIFEST_PATH        = "pwaManifestPath";

  @MockBean
  private SettingService        settingService;

  @MockBean
  private FileService           fileService;

  @MockBean
  private UploadService         uploadService;

  @MockBean
  private BrandingService       brandingService;

  @MockBean
  private ConfigurationManager  configurationManager;

  @MockBean
  private ExoFeatureService     featureService;

  @MockBean
  private ResourceBundleService resourceBundleService;

  @MockBean
  private ImageThumbnailService imageThumbnailService;

  @MockBean
  private ImageResizeService    imageResizeService;

  @MockBean
  private ChannelManager        channelManager;

  @MockBean
  private ListenerService       listenerService;

  @Autowired
  private PwaManifestService    pwaManifestService;

  @Mock
  private AbstractChannel       channel;

  @Mock
  private FileItem              smallIconFileItem;

  @Mock
  private FileInfo              smallIconFileInfo;

  @Mock
  private FileItem              largeIconFileItem;

  @Mock
  private FileInfo              largeIconFileInfo;

  @Mock
  private Branding              branding;

  @Mock
  private Logo                  brandingLogo;

  @Mock
  private Favicon               brandingFavicon;

  @Test
  public void initWithMinimalData() {
    when(channelManager.getChannel(ChannelKey.key(DEPRECATED_PUSH_CHANNEL_ID))).thenReturn(channel);

    pwaManifestService.init();

    assertFalse(pwaManifestService.isPwaEnabled());
    verify(channel).setEnabled(true);

    assertEquals(pwaManifestService.getPwaManifest().getManifestId(), MANIFEST_ID);
    assertEquals(pwaManifestService.getPwaManifest().getVersion(), MANIFEST_VERSION);
    assertEquals(pwaManifestService.getPwaManifest().getDescriptionKey(), MANIFEST_DESCRIPTION_KEY);
    assertEquals(pwaManifestService.getPwaManifestPath(), PWA_MANIFEST_PATH);
    assertNull(pwaManifestService.getPwaManifest().getName());
    assertNull(pwaManifestService.getPwaManifest().getBackgroundColor());
    assertNull(pwaManifestService.getPwaManifest().getThemeColor());
    assertNull(pwaManifestService.getPwaManifest().getDescription());
    assertNull(pwaManifestService.getPwaManifest().getSmallIconPath());
    assertNull(pwaManifestService.getPwaManifest().getLargeIconPath());

    verify(listenerService, atLeast(1)).addListener(eq(BrandingService.BRANDING_UPDATED_EVENT), any());
  }

  @Test
  public void initWithFullData() throws Exception {
    mockWithStoredValues();

    pwaManifestService.init();

    assertTrue(pwaManifestService.isPwaEnabled());
    verify(channel).setEnabled(false);

    assertEquals(pwaManifestService.getPwaManifestPath(), PWA_MANIFEST_PATH);
    PwaManifest pwaManifest = pwaManifestService.getPwaManifest();
    assertNotNull(pwaManifest);
    assertNotNull(pwaManifest.getTemplate());

    assertEquals(MANIFEST_ID, pwaManifest.getManifestId());
    assertEquals(MANIFEST_VERSION, pwaManifest.getVersion());
    assertEquals(MANIFEST_DESCRIPTION_KEY, pwaManifest.getDescriptionKey());
    assertEquals(MANIFEST_NAME, pwaManifest.getName());
    assertEquals(BACKGROUND_COLOR, pwaManifest.getBackgroundColor());
    assertEquals(THEME_COLOR, pwaManifest.getThemeColor());
    assertEquals(MANIFEST_DESCRIPTION, pwaManifest.getDescription());
    assertNotNull(pwaManifest.getSmallIconPath());
    assertNotNull(pwaManifest.getLargeIconPath());
    assertTrue(pwaManifest.getSmallIconPath().contains(PWA_SMALL_ICON_BASE_PATH));
    assertTrue(pwaManifest.getLargeIconPath().contains(PWA_LARGE_ICON_BASE_PATH));

    verify(listenerService, atLeast(1)).addListener(eq(BrandingService.BRANDING_UPDATED_EVENT), any());
  }

  @Test
  public void initWithBrandingData() throws Exception {
    mockWithBrandingValues();
    pwaManifestService.init();

    assertTrue(pwaManifestService.isPwaEnabled());
    verify(channel).setEnabled(false);

    assertEquals(pwaManifestService.getPwaManifestPath(), PWA_MANIFEST_PATH);
    PwaManifest pwaManifest = pwaManifestService.getPwaManifest();
    assertNotNull(pwaManifest);
    assertNotNull(pwaManifest.getTemplate());

    assertEquals(MANIFEST_ID, pwaManifest.getManifestId());
    assertEquals(MANIFEST_VERSION, pwaManifest.getVersion());
    assertEquals(MANIFEST_DESCRIPTION_KEY, pwaManifest.getDescriptionKey());
    assertEquals(MANIFEST_NAME, pwaManifest.getName());
    assertNull(pwaManifest.getBackgroundColor());
    assertNull(pwaManifest.getThemeColor());
    assertEquals(MANIFEST_DESCRIPTION, pwaManifest.getDescription());
    assertNotNull(pwaManifest.getSmallIconPath());
    assertNotNull(pwaManifest.getLargeIconPath());
    assertTrue(pwaManifest.getSmallIconPath().contains(PWA_SMALL_ICON_BASE_PATH));
    assertTrue(pwaManifest.getLargeIconPath().contains(PWA_LARGE_ICON_BASE_PATH));

    assertEquals(PRIMARY_COLOR, pwaManifestService.getThemeColor());

    verify(listenerService, atLeast(1)).addListener(eq(BrandingService.BRANDING_UPDATED_EVENT), any());
  }

  @Test
  public void getManifestHash() throws Exception {
    mockBrandingAttributes();

    long manifestHash = pwaManifestService.getManifestHash();
    assertNotEquals(0l, manifestHash);
    assertEquals(manifestHash, pwaManifestService.getManifestHash()); // No
                                                                      // change

    mockWithStoredValues();
    pwaManifestService.refreshManifest();
    assertNotEquals(manifestHash, pwaManifestService.getManifestHash());
  }

  @Test
  public void getSmallIcon() throws Exception {
    mockWithStoredValues();
    pwaManifestService.init();

    ManifestIcon smallIcon = pwaManifestService.getSmallIcon();
    assertNotNull(smallIcon);
    assertEquals(smallIconFileItem.getAsByte().length, smallIcon.getData().length);
  }

  @Test
  public void getSmallIconWithNoDimensions() throws Exception {
    mockWithStoredValues();
    pwaManifestService.init();

    ManifestIcon smallIcon = pwaManifestService.getSmallIcon(null);
    assertNotNull(smallIcon);
    assertEquals(smallIconFileItem.getAsByte().length, smallIcon.getData().length);

    smallIcon = pwaManifestService.getSmallIcon("test");
    assertNotNull(smallIcon);
    assertEquals(smallIconFileItem.getAsByte().length, smallIcon.getData().length);
  }

  @Test
  public void getLargeIcon() throws Exception {
    mockWithStoredValues();
    pwaManifestService.init();

    ManifestIcon largeIcon = pwaManifestService.getLargeIcon();
    assertNotNull(largeIcon);
    assertEquals(largeIconFileItem.getAsByte().length, largeIcon.getData().length);
  }

  @Test
  public void getLargeIconWithNoDimensions() throws Exception {
    mockWithStoredValues();
    pwaManifestService.init();

    ManifestIcon largeIcon = pwaManifestService.getLargeIcon(null);
    assertNotNull(largeIcon);
    assertEquals(largeIconFileItem.getAsByte().length, largeIcon.getData().length);

    largeIcon = pwaManifestService.getLargeIcon("test2");
    assertNotNull(largeIcon);
    assertEquals(largeIconFileItem.getAsByte().length, largeIcon.getData().length);
  }

  @Test
  public void getLargeIconWithDimensions() throws Exception {
    mockWithStoredValues();
    pwaManifestService.init();

    FileItem thumbnailFileItem = mock(FileItem.class);
    FileInfo thumbnailFileInfo = mock(FileInfo.class);
    when(thumbnailFileItem.getFileInfo()).thenReturn(thumbnailFileInfo);
    when(imageThumbnailService.getOrCreateThumbnail(any(),
                                                    eq(largeIconFileItem),
                                                    eq(100),
                                                    eq(100))).thenReturn(thumbnailFileItem);
    when(thumbnailFileItem.getAsStream()).thenAnswer(invocation -> new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
    when(thumbnailFileInfo.getId()).thenReturn(5l);
    ManifestIcon largeIcon = pwaManifestService.getLargeIcon("100x100");
    assertNotNull(largeIcon);
    assertEquals(3, largeIcon.getData().length);
    assertEquals(5l, largeIcon.getFileId());
  }

  @Test
  public void updateManifest() throws Exception {
    mockSettingsService();
    mockFeatureService();
    mockManifestFileContent();
    mockBrandingAttributes();
    mockIconFiles();

    pwaManifestService.init();

    PwaManifestUpdate manifestUpdate = new PwaManifestUpdate();
    manifestUpdate.setEnabled(true);
    manifestUpdate.setName("Name2");
    manifestUpdate.setDescription("Description2");
    manifestUpdate.setLargeIconUploadId("largeIconUploadId");
    manifestUpdate.setSmallIconUploadId("smallIconUploadId");

    manifestUpdate.setThemeColor("themeColor2");
    manifestUpdate.setBackgroundColor("eval");

    assertThrows(IllegalArgumentException.class, () -> pwaManifestService.updateManifest(manifestUpdate, TEST_USER));

    manifestUpdate.setThemeColor("eval");
    manifestUpdate.setBackgroundColor("backgroundColor2");
    assertThrows(IllegalArgumentException.class, () -> pwaManifestService.updateManifest(manifestUpdate, TEST_USER));

    manifestUpdate.setThemeColor("themeColor2");
    manifestUpdate.setBackgroundColor("backgroundColor2");
    assertThrows(IllegalArgumentException.class, () -> pwaManifestService.updateManifest(manifestUpdate, TEST_USER));

    when(fileService.writeFile(any())).thenAnswer(invocation -> {
      FileItem fileItem = invocation.getArgument(0);
      FileInfo fileInfo = mock(FileInfo.class);
      fileItem.setFileInfo(fileInfo);
      when(fileInfo.getId()).thenReturn(RANDOM.nextLong());
      return fileItem;
    });

    UploadResource largeIconUploadResource = mock(UploadResource.class);
    when(uploadService.getUploadResource(manifestUpdate.getLargeIconUploadId())).thenReturn(largeIconUploadResource);
    when(largeIconUploadResource.getStoreLocation()).thenReturn(getClass().getClassLoader()
                                                                          .getResource("test_icon.png")
                                                                          .getPath());
    UploadResource smallIconUploadResource = mock(UploadResource.class);
    when(uploadService.getUploadResource(manifestUpdate.getSmallIconUploadId())).thenReturn(smallIconUploadResource);
    when(smallIconUploadResource.getStoreLocation()).thenReturn(getClass().getClassLoader()
                                                                          .getResource("test_icon.png")
                                                                          .getPath());
    pwaManifestService.updateManifest(manifestUpdate, TEST_USER);
    assertTrue(pwaManifestService.isPwaEnabled());
    PwaManifest pwaManifest = pwaManifestService.getPwaManifest();
    assertNotNull(pwaManifest);
    assertEquals(manifestUpdate.getName(), pwaManifest.getName());
    assertEquals(manifestUpdate.getDescription(), pwaManifest.getDescription());
    assertEquals(manifestUpdate.getThemeColor(), pwaManifest.getThemeColor());
    assertEquals(manifestUpdate.getBackgroundColor(), pwaManifest.getBackgroundColor());
    assertNotEquals(Long.parseLong(LARGE_ICON_FILE_ID), pwaManifestService.getLargeIcon().getFileId());
    assertNotEquals(Long.parseLong(SMALL_ICON_FILE_ID), pwaManifestService.getSmallIcon().getFileId());
  }

  private void mockWithStoredValues() throws Exception {
    when(channelManager.getChannel(ChannelKey.key(DEPRECATED_PUSH_CHANNEL_ID))).thenReturn(channel);
    when(featureService.isActiveFeature(PWA_FEATURE)).thenReturn(true);
    mockManifestFileContent();
    mockSettingValue(PWA_NAME, MANIFEST_NAME);
    mockSettingValue(PWA_DESCRIPTION, MANIFEST_DESCRIPTION);
    mockSettingValue(PWA_BACKGROUND_COLOR, BACKGROUND_COLOR);
    mockSettingValue(PWA_THEME_COLOR, THEME_COLOR);

    mockIconFiles();
  }

  private void mockWithBrandingValues() throws Exception {
    when(channelManager.getChannel(ChannelKey.key(DEPRECATED_PUSH_CHANNEL_ID))).thenReturn(channel);
    when(featureService.isActiveFeature(PWA_FEATURE)).thenReturn(true);
    when(brandingService.getDefaultLanguage()).thenReturn(Locale.ENGLISH.toLanguageTag());
    when(resourceBundleService.getSharedString(MANIFEST_DESCRIPTION_KEY, Locale.ENGLISH)).thenReturn(MANIFEST_DESCRIPTION);
    when(brandingService.getThemeStyle()).thenReturn(Collections.singletonMap("primaryColor", PRIMARY_COLOR));

    mockManifestFileContent();
    mockSettingValue(PWA_NAME, MANIFEST_NAME);

    mockBrandingIconFiles();
  }

  @SuppressWarnings("resource")
  private void mockManifestFileContent() throws Exception {
    when(configurationManager.getInputStream(PWA_MANIFEST_PATH)).thenAnswer(invocation -> getClass().getClassLoader()
                                                                                                    .getResourceAsStream("pwa/manifest.json"));
  }

  private void mockBrandingAttributes() {
    when(brandingService.getBrandingInformation(false)).thenReturn(branding);
    when(branding.getDefaultLanguage()).thenReturn(Locale.ENGLISH.toLanguageTag());
    when(branding.getDirection()).thenReturn("ltr");
  }

  private void mockBrandingIconFiles() {
    mockBrandingLogoFile();
    mockBrandingFaviconFile();
  }

  private void mockBrandingLogoFile() {
    when(brandingService.getFavicon()).thenReturn(brandingFavicon);
    when(brandingFavicon.getData()).thenAnswer(invocation -> new byte[] { 1, 2 });
    when(brandingFavicon.getFileId()).thenReturn(Long.parseLong(SMALL_ICON_FILE_ID));
    when(brandingFavicon.getSize()).thenReturn(2l);
    when(brandingFavicon.getUpdatedDate()).thenReturn(System.currentTimeMillis());
  }

  private void mockBrandingFaviconFile() {
    when(brandingService.getLogo()).thenReturn(brandingLogo);
    when(brandingLogo.getData()).thenAnswer(invocation -> new byte[] { 1 });
    when(brandingLogo.getFileId()).thenReturn(Long.parseLong(LARGE_ICON_FILE_ID));
    when(brandingLogo.getSize()).thenReturn(1l);
    when(brandingLogo.getUpdatedDate()).thenReturn(System.currentTimeMillis());
  }

  private void mockIconFiles() throws FileStorageException {
    mockLargeIconFile();
    mockSmallIconFile();
    mockSettingValue(PWA_SMALL_ICON, SMALL_ICON_FILE_ID);
    mockSettingValue(PWA_LARGE_ICON, LARGE_ICON_FILE_ID);
  }

  private void mockSmallIconFile() throws FileStorageException {
    when(fileService.getFile(Long.parseLong(SMALL_ICON_FILE_ID))).thenReturn(smallIconFileItem);
    when(smallIconFileItem.getAsByte()).thenAnswer(invocation -> new byte[] { 1, 2 });
    when(smallIconFileItem.getFileInfo()).thenReturn(smallIconFileInfo);
    when(smallIconFileInfo.getSize()).thenReturn(2l);
    when(smallIconFileInfo.getUpdatedDate()).thenReturn(new Date());
  }

  private void mockLargeIconFile() throws FileStorageException {
    when(fileService.getFile(Long.parseLong(LARGE_ICON_FILE_ID))).thenReturn(largeIconFileItem);
    when(largeIconFileItem.getAsByte()).thenAnswer(invocation -> new byte[] { 1 });
    when(largeIconFileItem.getFileInfo()).thenReturn(largeIconFileInfo);
    when(largeIconFileInfo.getSize()).thenReturn(1l);
    when(largeIconFileInfo.getUpdatedDate()).thenReturn(new Date());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void mockSettingValue(String key, String value) {
    when(settingService.get(Context.GLOBAL,
                            Scope.GLOBAL,
                            key)).thenReturn((SettingValue) SettingValue.create(value));
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void mockSettingsService() {
    doAnswer(invocation -> {
      Context context = invocation.getArgument(0);
      Scope scope = invocation.getArgument(1);
      String key = invocation.getArgument(2);
      SettingValue value = invocation.getArgument(3);
      when(settingService.get(context, scope, key)).thenReturn(value);
      return null;
    }).when(settingService)
      .set(any(),
           any(),
           any(),
           any());
  }

  private void mockFeatureService() {
    doAnswer(invocation -> {
      String featureName = invocation.getArgument(0);
      boolean enabled = invocation.getArgument(1);
      when(featureService.isActiveFeature(featureName)).thenReturn(enabled);
      return null;
    }).when(featureService).saveActiveFeature(any(), anyBoolean());
  }

}
