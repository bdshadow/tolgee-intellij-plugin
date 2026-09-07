package io.tolgee.intellij.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * All Tolgee balloons go through here. Keeps the notification group id in exactly one
 * place (also declared in plugin.xml).
 */
object TolgeeNotifications {

    private const val GROUP_ID = "Tolgee"

    fun info(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    fun warn(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }
}
