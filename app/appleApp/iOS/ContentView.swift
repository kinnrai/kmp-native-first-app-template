import SwiftUI

struct ContentView: View {
  @Environment(TaskStore.self) private var store

  @State private var filter: TaskListFilter? = .active
  @State private var searchText = ""
  @State private var editor: TaskEditorPresentation?
  @State private var isConfirmingClearCompleted = false

  var body: some View {
    NavigationSplitView {
      sidebar
    } detail: {
      NavigationStack {
        taskList
          .navigationDestination(for: String.self) { taskID in
            TaskDetailDestination(taskID: taskID)
          }
      }
    }
    .searchable(text: $searchText, prompt: "Search tasks")
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
  }

  private var sidebar: some View {
    List(selection: $filter) {
      Section("Tasks") {
        ForEach(TaskListFilter.allCases) { option in
          NavigationLink(value: option) {
            Label(option.title, systemImage: option.systemImage)
          }
          .badge(store.count(for: option))
        }
      }

      Section {
        SyncStatusView(status: store.syncStatus)
      }
    }
    .navigationTitle("Tasks")
  }

  private var taskList: some View {
    List {
      if let message = store.syncStatus.lastError {
        Section {
          Label(message, systemImage: "wifi.exclamationmark")
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
      }

      if visibleTasks.isEmpty {
        ContentUnavailableView(
          emptyTitle,
          systemImage: selectedFilter.systemImage,
          description: Text(emptyDescription)
        )
        .listRowBackground(Color.clear)
      } else {
        ForEach(visibleTasks) { task in
          NavigationLink(value: task.id) {
            TaskRow(task: task) {
              _Concurrency.Task {
                await store.toggleCompleted(taskID: task.id)
              }
            }
          }
          .swipeActions(edge: .leading, allowsFullSwipe: true) {
            if task.syncState != .conflict {
              Button {
                _Concurrency.Task {
                  await store.toggleCompleted(taskID: task.id)
                }
              } label: {
                Label(
                  task.isCompleted ? "Mark Active" : "Complete",
                  systemImage: task.isCompleted
                    ? "arrow.uturn.backward"
                    : "checkmark"
                )
              }
              .tint(.accentColor)
            }
          }
          .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if task.syncState != .conflict {
              Button(role: .destructive) {
                _Concurrency.Task {
                  await store.delete(taskID: task.id)
                }
              } label: {
                Label("Delete", systemImage: "trash")
              }
            }
          }
        }
      }
    }
    .navigationTitle(selectedFilter.title)
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

        Menu {
          Button("Clear Completed", systemImage: "trash") {
            isConfirmingClearCompleted = true
          }
          .disabled(store.clearableCompletedCount == 0)
        } label: {
          Label("More", systemImage: "ellipsis")
        }
      }
    }
    .overlay {
      if store.isStarting {
        ProgressView("Opening tasks…")
      }
    }
  }

  private var selectedFilter: TaskListFilter {
    filter ?? .active
  }

  private var visibleTasks: [TaskRecord] {
    store.filteredTasks(filter: selectedFilter, searchText: searchText)
  }

  private var emptyTitle: String {
    searchText.isEmpty ? "No \(selectedFilter.title) Tasks" : "No Results"
  }

  private var emptyDescription: String {
    searchText.isEmpty
      ? "Create a task or select another list."
      : "Try a different title or note."
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

private struct TaskDetailDestination: View {
  let taskID: String

  @Environment(\.dismiss) private var dismiss

  var body: some View {
    TaskDetailView(taskID: taskID) {
      dismiss()
    }
  }
}

#Preview {
  ContentView()
    .environment(TaskStore(baseURL: URL(string: "http://127.0.0.1:8080")!))
}
