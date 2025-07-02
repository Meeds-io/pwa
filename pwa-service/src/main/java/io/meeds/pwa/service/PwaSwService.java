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

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.portal.application.ResourceRequestFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import lombok.SneakyThrows;

@Service
public class PwaSwService {

  private static final Log               LOG                      = ExoLogger.getLogger(PwaSwService.class);

  private static final boolean           DEVELOPPING              = PropertyManager.isDevelopping();

  private static final String            OFFLINE_ENABLED_VARIABLE = "@pwa.offline.enabled@";

  private static final String            DEVELOPMENT_VARIABLE     = "@development@";

  private static final String            SW_EXTENSIONS            = "@service-worker-extensions@";

  private static final String            ASSETS_VERSION_VARIABLE  = "@assets-version@";

  private static AtomicReference<String> serviceWorkerContent     = new AtomicReference<>();

  @Autowired
  private PortalContainer                container;

  @Autowired
  private ConfigurationManager           configurationManager;

  @Value("${pwa.service.worker.path:jar:/pwa/service-worker.js}")
  private String                         serviceWorkerPath;

  @Value("${pwa.service.worker.extension.path:../../js/service-worker-extension.js}")
  private String                         serviceWorkerExtensionPath;

  @Value("${pwa.offline.enabled:false}")
  private boolean                        offlineEnabled;

  public boolean isOfflineEnabled() {
    return offlineEnabled;
  }

  public void setOfflineEnabled(boolean offlineEnabled) {
    this.offlineEnabled = offlineEnabled;
  }

  public String getContent() {
    if (serviceWorkerContent.get() == null || DEVELOPPING) {
      String content = getJavascriptContent(serviceWorkerPath);
      if (content != null) {
        content = replaceExtensions(content);
        content = replaceVariables(content);
        serviceWorkerContent.set(DEVELOPPING ? content : minify(content));
      }
    }
    return serviceWorkerContent.get();
  }

  private String replaceVariables(String content) {
    if (ResourceRequestFilter.version != null) {
      content = content.replace(ASSETS_VERSION_VARIABLE, ResourceRequestFilter.version);
    }
    content = content.replace(OFFLINE_ENABLED_VARIABLE, String.valueOf(offlineEnabled));
    content = content.replace(DEVELOPMENT_VARIABLE, String.valueOf(DEVELOPPING));
    return content;
  }

  @SneakyThrows
  private String replaceExtensions(String content) {
    if (StringUtils.isBlank(serviceWorkerExtensionPath)) {
      content = content.replace(SW_EXTENSIONS, "");
    } else if (container.getPortalClassLoader() != null) {
      Enumeration<URL> resources = container.getPortalClassLoader().getResources(serviceWorkerExtensionPath);
      if (resources != null) {
        StringBuilder extensionContents = new StringBuilder();
        while (resources.hasMoreElements()) {
          URL url = resources.nextElement();
          try (InputStream is = url.openStream()) {
            String extensionContent = IOUtils.toString(is, StandardCharsets.UTF_8);
            if (extensionContent != null) {
              extensionContents.append(extensionContent)
                               .append("\n");
            }
          }
        }
        content = content.replace(SW_EXTENSIONS, extensionContents.toString());
      } else {
        content = content.replace(SW_EXTENSIONS, "");
      }
    } else {
      content = content.replace(SW_EXTENSIONS, "");
    }
    return content;
  }

  @SneakyThrows
  private String minify(String javascript) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
    options.setStrictModeInput(false);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.QUIET.setOptionsForWarningLevel(options);
    SourceFile extern = SourceFile.fromCode("extern", "");
    javascript = SourceFile.fromCode("code", javascript).getCode();
    SourceFile jsInput = SourceFile.fromCode("jsInput", javascript);
    compiler.compile(extern, jsInput, options);
    return compiler.toSource();
  }

  private String getJavascriptContent(String path) {
    try (InputStream is = configurationManager.getInputStream(path)) {
      if (is != null) {
        return IOUtils.toString(is, StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      LOG.warn("Can't find service worker path: {}", path, e);
    }
    return null;
  }

}
