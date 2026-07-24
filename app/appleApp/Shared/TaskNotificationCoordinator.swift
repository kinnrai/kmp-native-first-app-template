import Observation
import UserNotifications

struct TaskNotificationRoute: Equatable {
  let taskID: String
  let requestID = UUID()
}

@MainActor
@Observable
final class TaskNotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
  private(set) var route: TaskNotificationRoute?

  @ObservationIgnored private let center: UNUserNotificationCenter

  init(center: UNUserNotificationCenter = .current()) {
    self.center = center
    super.init()
    center.delegate = self
  }

  func consumeRoute() {
    route = nil
  }

  nonisolated func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    completionHandler([.banner, .sound])
  }

  nonisolated func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let taskID = response.notification.request.content.userInfo["taskID"] as? String
    if let taskID {
      _Concurrency.Task { @MainActor [weak self] in
        self?.route = TaskNotificationRoute(taskID: taskID)
      }
    }
    completionHandler()
  }
}
