import SwiftUI

@main
struct IOSApp: App {
  @State private var store = TaskStore(databaseName: "ios-tasks.db")
  @State private var notifications = TaskNotificationCoordinator()

  var body: some Scene {
    WindowGroup {
      ContentView()
        .environment(store)
        .environment(notifications)
        .task {
          await store.start()
        }
    }
  }
}
