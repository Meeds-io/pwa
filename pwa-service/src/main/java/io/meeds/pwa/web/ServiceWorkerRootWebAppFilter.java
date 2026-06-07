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
package io.meeds.pwa.web;

import java.io.IOException;

import org.springframework.stereotype.Service;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.WebAppInitContext;

import io.meeds.web.security.plugin.RootWebappFilterPlugin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class ServiceWorkerRootWebAppFilter implements RootWebappFilterPlugin {

  private static final String URI                     = "/service-worker.js";

  private static final String SERVICE_WORKER_CONTEXT  = "/pwa";

  private static final String SERVICE_WORKER_REST_URI = "/rest/service-worker";

  @Override
  public boolean matches(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    return URI.equals(httpRequest.getRequestURI());
  }

  @Override
  public void doFilter(HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse,
                       FilterChain chain) throws IOException, ServletException {
    httpResponse.setHeader("Service-Worker-Allowed", "/");
    httpResponse.setHeader("Cache-Control", "no-cache");
    httpResponse.setHeader("Content-Type", "text/javascript");
    PortalContainer.getInstance()
                   .getServletContexts()
                   .stream()
                   .filter(s -> s.getServletContext().getContextPath().equals(SERVICE_WORKER_CONTEXT))
                   .map(WebAppInitContext::getServletContext)
                   .findFirst()
                   .orElseThrow()
                   .getRequestDispatcher(SERVICE_WORKER_REST_URI)
                   .forward(httpRequest, httpResponse);
  }

}
