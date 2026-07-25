import Observation
import SwiftUI

@MainActor
@Observable
final class MacTaskCommandModel {
  var selectedTaskID: String?
  var newTaskRequest = UUID()
  var clearCompletedRequest = UUID()

  func requestNewTask() {
    newTaskRequest = UUID()
  }

  func requestClearCompleted() {
    clearCompletedRequest = UUID()
  }
}

struct ContentView: View {
  @Environment(TaskStore.self) private var store
  @Environment(MacTaskCommandModel.self) private var commands

  @State private var filter = TaskListFilter.inbox
  @State private var searchText = ""
  @State private var editor: TaskEditorPresentation?
  @State private var isConfirmingClearCompleted = false

  var body: some View {
    @Bindable var commands = commands

    NavigationSplitView {
      sidebar
    } content: {
      List(selection: $commands.selectedTaskID) {
        if visibleTasks.isEmpty {
          ContentUnavailableView(
            searchText.isEmpty ? "No \(filter.title) Tasks" : "No Results",
            systemImage: filter.systemImage,
            description: Text(
              searchText.isEmpty
                ? "Create a task or select another list."
                : "Try a different title or note."
            )
          )
          .listRowBackground(Color.clear)
        } else {
          ForEach(visibleTasks) { task in
            TaskRow(task: task) {
              _Concurrency.Task {
                await store.toggleCompleted(taskID: task.id)
              }
            }
            .tag(task.id)
            .contextMenu {
              Button(task.isCompleted ? "Mark Active" : "Complete") {
                _Concurrency.Task {
                  await store.toggleCompleted(taskID: task.id)
                }
              }
              .disabled(task.syncState == .conflict)

              Button("Delete", role: .destructive) {
                _Concurrency.Task {
                  await store.delete(taskID: task.id)
                }
              }
              .disabled(task.syncState == .conflict)
            }
          }
        }
      }
      .navigationTitle(filter.title)
      .navigationSplitViewColumnWidth(min: 280, ideal: 340)
      .searchable(text: $searchText, prompt: "Search tasks")
    } detail: {
      if let taskID = commands.selectedTaskID {
        TaskDetailView(taskID: taskID) {
          commands.selectedTaskID = nil
        }
      } else {
        ContentUnavailableView(
          "Select a Task",
          systemImage: "checklist",
          description: Text("Choose a task to see its details.")
        )
      }
    }
    .frame(minWidth: 760, minHeight: 520)
    .toolbar {
      ToolbarItemGroup(placement: .primaryAction) {
        Button {
          _Concurrency.Task {
            await store.sync()
          }
        } label: {
          Label("Sync", systemImage: "arrow.trianglehead.2.clockwise.rotate.90")
        }
        .disabled(store.syncStatus.phase == .syncing)

        Button {
          editor = TaskEditorPresentation(mode: .create)
        } label: {
          Label("New Task", systemImage: "plus")
        }
      }
    }
    .sheet(item: $editor) { presentation in
      TaskEditorView(presentation: presentation) { draft in
        save(presentation, draft: draft)
      }
    }
    .alert("Clear completed tasks?", isPresented: $isConfirmingClearCompleted) {
      Button("Clear", role: .destructive) {
        _Concurrency.Task {
          await store.clearCompleted()
        }
      }
      Button("Cancel", role: .cancel) {}
    } message: {
      Text("Completed tasks are deleted locally and synchronized later if you are offline.")
    }
    .alert(
      "Task operation failed",
      isPresented: Binding(
        get: { store.presentedError != nil },
        set: { if !$0 { store.dismissError() } }
      )
    ) {
      Button("OK") {
        store.dismissError()
      }
    } message: {
      Text(store.presentedError?.message ?? "")
    }
    .onChange(of: commands.newTaskRequest) {
      editor = TaskEditorPresentation(mode: .create)
    }
    .onChange(of: commands.clearCompletedRequest) {
      isConfirmingClearCompleted = true
    }
    .onChange(of: filter) {
      if let selection = commands.selectedTaskID,
        !visibleTasks.contains(where: { $0.id == selection })
      {
        commands.selectedTaskID = nil
      }
    }
  }

  private var sidebar: some View {
    List(selection: $filter) {
      Section("Tasks") {
        ForEach(TaskListFilter.allCases) { option in
          Label(option.title, systemImage: option.systemImage)
            .badge(store.count(for: option))
            .tag(option)
        }
      }

      Section("Synchronization") {
        SyncStatusView(status: store.syncStatus)
        if let message = store.syncStatus.lastError {
          Text(message)
            .font(.caption)
            .foregroundStyle(.secondary)
            .lineLimit(3)
        }
      }
    }
    .navigationTitle("Tasks")
    .navigationSplitViewColumnWidth(min: 180, ideal: 220)
  }

  private var visibleTasks: [TaskRecord] {
    store.filteredTasks(filter: filter, searchText: searchText)
  }

  private func save(
    _ presentation: TaskEditorPresentation,
    draft: TaskEditorDraft
  ) {
    _Concurrency.Task {
      switch presentation.mode {
      case .create:
        await store.create(draft)
      case .edit(let task):
        await store.update(taskID: task.id, draft: draft)
      case .merge(let conflict):
        await store.mergeConflict(taskID: conflict.id, draft: draft)
      }
    }
  }
}

struct MacTaskCommands: Commands {
  let store: TaskStore
  let commands: MacTaskCommandModel

  var body: some Commands {
    CommandGroup(replacing: .newItem) {
      Button("New Task") {
        commands.requestNewTask()
      }
      .keyboardShortcut("n", modifiers: .command)
    }

    CommandMenu("Tasks") {
      Button("Synchronize") {
        _Concurrency.Task {
          await store.sync()
        }
      }
      .keyboardShortcut("r", modifiers: [.command, .shift])
      .disabled(store.syncStatus.phase == .syncing)

      Divider()

      Button("Toggle Completed") {
        guard let taskID = commands.selectedTaskID else { return }
        _Concurrency.Task {
          await store.toggleCompleted(taskID: taskID)
        }
      }
      .keyboardShortcut(.space, modifiers: [])
      .disabled(!canToggleSelectedTask)

      Button("Clear Completed") {
        commands.requestClearCompleted()
      }
      .disabled(store.clearableCompletedCount == 0)
    }
  }

  private var canToggleSelectedTask: Bool {
    guard
      let taskID = commands.selectedTaskID,
      let task = store.task(id: taskID)
    else {
      return false
    }
    return task.syncState != .conflict
  }
}

#Preview {
  ContentView()
    .environment(TaskStore(baseURL: URL(string: "http://127.0.0.1:8080")!))
    .environment(MacTaskCommandModel())
}
