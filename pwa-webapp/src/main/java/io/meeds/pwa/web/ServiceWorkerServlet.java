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
package io.meeds.pwa.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.gatein.portal.controller.resource.ResourceRequestHandler;

import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.web.AbstractHttpServlet;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ServiceWorkerServlet extends AbstractHttpServlet {

  private static final long              serialVersionUID            = 3739991860557358896L;

  private static final Log               LOG                         = ExoLogger.getLogger(ServiceWorkerServlet.class);

  private static final boolean           DEVELOPPING                 = PropertyManager.isDevelopping();

  private static final String            DEVELOPMENT_VARIABLE        = "@development@";

  private static final String            ASSETS_VERSION_VARIABLE     = "@assets-version@";

  private static final String            SERVICE_WORKER_PATH_PARAM   = "pwa.service.worker.path";

  private static final String            DEFAULT_SERVICE_WORKER_PATH = "jar:/pwa/service-worker.js";     // NOSONAR

  private static AtomicReference<String> serviceWorkerContent        = new AtomicReference<>();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
    String content = getContent(response);
    if (content == null) {
      return;
    }

    try {
      response.setHeader("Service-Worker-Allowed", "/");
      response.setHeader("Cache-Control", "max-age=31536000");
      response.setHeader("Content-Type", "text/javascript");
      response.setHeader("Etag", "W/\"" + content.hashCode() + "\"");

      PrintWriter writer = response.getWriter();
      writer.append(content);
      writer.flush();
    } catch (Exception e) {
      LOG.warn("Error retrieving service worker content", e);
      response.setStatus(500);
    }
  }

  private String getContent(HttpServletResponse response) {
    if (serviceWorkerContent.get() == null || DEVELOPPING) {
      String serviceWorkerPath = PropertyManager.getProperty(SERVICE_WORKER_PATH_PARAM);
      if (serviceWorkerPath == null) {
        serviceWorkerPath = DEFAULT_SERVICE_WORKER_PATH;
      }
      try {
        ConfigurationManager configurationManager = PortalContainer.getInstance()
                                                                   .getComponentInstanceOfType(ConfigurationManager.class);

        URL resourceURL = configurationManager.getResource(serviceWorkerPath);
        String filePath = resourceURL.getPath();
        String content = IOUtil.getFileContentAsString(filePath, "UTF-8");
        content = replaceVariables(content);
        serviceWorkerContent.set(content);
      } catch (Exception e) {
        LOG.warn("Can't find service worker path: {}", serviceWorkerPath);
        response.setStatus(404);
        return null;
      }
    }
    return serviceWorkerContent.get();
  }

  private String replaceVariables(String content) {
    content = content.replace(ASSETS_VERSION_VARIABLE, ResourceRequestHandler.VERSION);
    content = content.replace(DEVELOPMENT_VARIABLE, String.valueOf(DEVELOPPING));
    return content;
  }

}
