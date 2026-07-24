import SwiftUI

@main
struct IOSApp: App {
  @State private var store = TaskStore(databaseName: "ios-tasks.db")

  var body: some Scene {
    WindowGroup {
      ContentView()
        .environment(store)
        .task {
          await store.start()
        }
    }
  }
}
