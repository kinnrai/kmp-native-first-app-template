import Foundation
import OSLog
import UserNotifications

@MainActor
protocol TaskReminderScheduling: AnyObject {
  func requestAuthorization() async -> Bool
  func reconcile(tasks: [TaskRecord])
}

@MainActor
final class TaskReminderScheduler: TaskReminderScheduling {
  private let center: UNUserNotificationCenter
  private let identifierPrefix = "task-reminder."
  private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "com.example.kmpnativefirst",
    category: "TaskReminders"
  )
  private var reconciliationTask: _Concurrency.Task<Void, Never>?

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
  }

  func requestAuthorization() async -> Bool {
    let settings = await center.notificationSettings()
    switch settings.authorizationStatus {
    case .authorized, .provisional, .ephemeral:
      return true
    case .denied:
      return false
    case .notDetermined:
      do {
        return try await center.requestAuthorization(options: [.alert, .sound, .badge])
      } catch {
        return false
      }
    @unknown default:
      return false
    }
  }

  func reconcile(tasks: [TaskRecord]) {
    let now = Date()
    let desiredRequests = tasks
      .filter { task in
        !task.isCompleted
          && task.syncState != .conflict
          && task.reminderAt.map { $0 > now } == true
      }
      .compactMap(makeRequest)
    let previousReconciliation = reconciliationTask
    reconciliationTask = _Concurrency.Task { [weak self] in
      await previousReconciliation?.value
      guard let self else { return }
      await apply(desiredRequests)
    }
  }

  private func apply(_ desiredRequests: [UNNotificationRequest]) async {
    let desiredIDs = Set(desiredRequests.map(\.identifier))
    let pendingRequests = await center.pendingNotificationRequests()
    let managedIDs = Set(
      pendingRequests.map(\.identifier).filter { $0.hasPrefix(identifierPrefix) }
    )
    let staleIDs = Array(managedIDs.subtracting(desiredIDs))

    if !staleIDs.isEmpty {
      center.removePendingNotificationRequests(withIdentifiers: staleIDs)
    }
    for request in desiredRequests {
      do {
        try await center.add(request)
      } catch {
        logger.error(
          "Unable to schedule reminder \(request.identifier, privacy: .public): \(error.localizedDescription, privacy: .public)"
        )
      }
    }
  }

  private func makeRequest(task: TaskRecord) -> UNNotificationRequest? {
    guard let reminderAt = task.reminderAt else { return nil }

    var components = Calendar.autoupdatingCurrent.dateComponents(
      [.year, .month, .day, .hour, .minute],
      from: reminderAt
    )
    components.timeZone = Calendar.autoupdatingCurrent.timeZone

    let content = UNMutableNotificationContent()
    content.title = "Task reminder"
    content.body = task.title
    content.sound = .default
    content.userInfo = ["taskID": task.id]

    let trigger = UNCalendarNotificationTrigger(
      dateMatching: components,
      repeats: false
    )
    return UNNotificationRequest(
      identifier: identifierPrefix + task.id,
      content: content,
      trigger: trigger
    )
  }
}
