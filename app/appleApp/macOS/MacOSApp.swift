import SwiftUI

@main
struct MacOSApp: App {
  @State private var store = TaskStore(databaseName: "macos-tasks.db")
  @State private var commands = MacTaskCommandModel()
  @State private var notifications = TaskNotificationCoordinator()

  var body: some Scene {
    WindowGroup {
      ContentView()
        .environment(store)
        .environment(commands)
        .environment(notifications)
        .task {
          await store.start()
        }
    }
    .defaultSize(width: 1_080, height: 720)
    .commands {
      MacTaskCommands(store: store, commands: commands)
    }
  }
}
