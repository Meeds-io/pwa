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
package io.meeds.pwa.listener;

import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_DELETED_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_READ_ALL_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_READ_EVENT;
import static org.exoplatform.commons.api.notification.service.storage.WebNotificationStorage.NOTIFICATION_WEB_SAVED_EVENT;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.pwa.service.PwaNotificationService;

@SpringBootTest(classes = {
                            WebNotificationSentListener.class,
})
@ExtendWith(MockitoExtension.class)
public class WebNotificationSentListenerTest {

  private static final String         NOTIFICATION_ID = "5";

  @MockBean
  private PwaNotificationService      pwaNotificationService;

  @MockBean
  private ListenerService             listenerService;

  @Autowired
  private WebNotificationSentListener webNotificationSentListener;

  @Mock
  private Event<Object, Object>       event;

  @Test
  public void init() {
    webNotificationSentListener.init();
    verify(listenerService, times(1)).addListener(anyString(), eq(webNotificationSentListener));
    verify(listenerService).addListener(NOTIFICATION_WEB_SAVED_EVENT, webNotificationSentListener);
  }

  @Test
  public void onEventWhenNotHandled() throws Exception {
    webNotificationSentListener.onEvent(event);
    verifyNoMoreInteractions(pwaNotificationService);
  }

  @Test
  public void onEventWhenWebNotifUpdated() throws Exception {
    when(event.getEventName()).thenReturn(NOTIFICATION_WEB_SAVED_EVENT);
    when(event.getData()).thenReturn(false);
    webNotificationSentListener.onEvent(event);
    verifyNoMoreInteractions(pwaNotificationService);
  }

  @Test
  public void onEventWhenWebNotifCreated() throws Exception {
    when(event.getEventName()).thenReturn(NOTIFICATION_WEB_SAVED_EVENT);
    when(event.getData()).thenReturn(true);
    when(event.getSource()).thenReturn(NOTIFICATION_ID);
    webNotificationSentListener.onEvent(event);
    verify(pwaNotificationService).create(Long.parseLong(NOTIFICATION_ID));
  }

}
