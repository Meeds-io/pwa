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
package io.meeds.pwa.plugin.social;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationAction;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;

import lombok.Getter;

public abstract class BaseActivityPwaNotificationPlugin implements PwaNotificationPlugin {

  private static final String     PWA_NOTIFICATION_MARK_LIKE_ACTION = "like";

  private static final String     PWA_NOTIFICATION_MARK_LIKE_LABEL  = "pwa.notification.action.like";

  @Autowired
  protected ResourceBundleService resourceBundleService;

  @Autowired
  protected PermanentLinkService  permanentLinkService;

  @Autowired
  @Getter
  protected ActivityManager       activityManager;

  @Autowired
  @Getter
  protected IdentityManager       identityManager;

  @Override
  public void handleAction(NotificationInfo notification, String action, String username) {
    if (StringUtils.equals(action, PWA_NOTIFICATION_MARK_LIKE_ACTION)) {
      String activityId = getPostId(notification);
      ExoSocialActivity activity = activityManager.getActivity(activityId);
      Identity identity = identityManager.getOrCreateUserIdentity(username);
      activityManager.saveLike(activity, identity);
    }
  }

  public void process(PwaNotificationMessage notificationMessage,
                      NotificationInfo notification,
                      LocaleConfig localeConfig) {
    List<PwaNotificationAction> notificationActions = notificationMessage.getActions();
    if (CollectionUtils.isEmpty(notificationMessage.getActions())
        || notificationActions.stream()
                              .noneMatch(a -> StringUtils.equals(a.getAction(), PWA_NOTIFICATION_MARK_LIKE_ACTION))) {
      String activityId = getPostId(notification);
      ExoSocialActivity activity = activityManager.getActivity(activityId);
      String username = notification.getTo();
      String identityId = StringUtils.isBlank(username) ? null : identityManager.getOrCreateUserIdentity(username).getId();
      if (StringUtils.isNotBlank(identityId)
          && !StringUtils.equals(activity.getPosterId(), identityId)
          && (activity.getLikeIdentityIds() == null
              || Stream.of(activity.getLikeIdentityIds()).noneMatch(id -> StringUtils.equals(id, identityId)))) {
        notificationActions = notificationActions == null ? new ArrayList<>() : new ArrayList<>(notificationActions);
        notificationActions.add(new PwaNotificationAction(resourceBundleService.getSharedString(PWA_NOTIFICATION_MARK_LIKE_LABEL,
                                                                                                localeConfig.getLocale()),
                                                          PWA_NOTIFICATION_MARK_LIKE_ACTION));
        notificationMessage.setActions(notificationActions);
      }
    }
  }

  public String getPostId(NotificationInfo notification) {
    String activityId = notification.getValueOwnerParameter(SocialNotificationUtils.ACTIVITY_ID.getKey());
    String commentId = notification.getValueOwnerParameter(SocialNotificationUtils.COMMENT_ID.getKey());
    String replyToCommentId = notification.getValueOwnerParameter(SocialNotificationUtils.COMMENT_REPLY_ID.getKey());
    return Arrays.asList(replyToCommentId, commentId, activityId).stream().filter(Objects::nonNull).findFirst().orElse(null);
  }

  public String getPostContent(NotificationInfo notification) {
    ExoSocialActivity post = getActivityManager().getActivity(getPostId(notification));
    return post == null ? "" : getActivityContent(post);
  }

  public String getActivityContent(NotificationInfo notification) {
    ExoSocialActivity activity = getActivity(notification);
    return activity == null ? "" : getActivityContent(activity);
  }

  public String getCommentContent(NotificationInfo notification) {
    ExoSocialActivity comment = getComment(notification);
    return comment == null ? "" : getActivityContent(comment);
  }

  private String getActivityContent(ExoSocialActivity activity) {
    String html = getActivityManager().getActivityTitle(activity);
    return htmlToText(html);
  }

  public ExoSocialActivity getActivity(NotificationInfo notification) {
    String activityId = notification.getValueOwnerParameter(SocialNotificationUtils.ACTIVITY_ID.getKey());
    ExoSocialActivity activity = getActivityManager().getActivity(activityId);
    if (activity.isComment()) {
      // we need to build the content of activity by type, so if it's a comment,
      // we will get the parent activity
      activity = getActivityManager().getParentActivity(activity);
    }
    return activity;
  }

  public ExoSocialActivity getComment(NotificationInfo notification) {
    String commentId = notification.getValueOwnerParameter(SocialNotificationUtils.COMMENT_ID.getKey());
    return commentId == null ? null : getActivityManager().getActivity(commentId);
  }

  protected String getSender(NotificationInfo notification) {
    String username = notification.getFrom();
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("poster");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("username");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("profile");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("sender");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("modifier");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("MODIFIER_ID");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("SENDER_ID");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("request_from");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("likersId");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("creator");
    }
    if (StringUtils.isBlank(username)) {
      username = notification.getOwnerParameter().get("creatorId");
    }
    if (StringUtils.isBlank(username)) {
      return null;
    } else {
      Identity identity = identityManager.getOrCreateUserIdentity(username);
      if (identity != null) {
        return identity.getRemoteId();
      }
    }
    if (StringUtils.isNumeric(username)) {
      Identity identity = identityManager.getIdentity(username);
      if (identity != null) {
        return identity.getRemoteId();
      }
    }
    return null;
  }

}
