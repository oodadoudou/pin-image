package app.pinimage.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service is fully implemented in a later commit. The class is
 * declared here so the permission check in the main UI can reference it.
 */
class ScreenshotAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
