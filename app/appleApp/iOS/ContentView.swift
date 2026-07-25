import SwiftUI

private struct TaskNotificationNavigationState: Equatable {
  let route: TaskNotificationRoute?
  let isAvailable: Bool
}

struct ContentView: View {
  @Environment(TaskStore.self) private var store
  @Environment(TaskNotificationCoordinator.self) private var notifications

  @State private var selection: TaskCollectionSelection? = .smart(.inbox)
  @State private var searchText = ""
  @State private var taskPath: [String] = []
  @State private var taskEditor: TaskEditorPresentation?
  @State private var projectEditor: TaskProjectEditorPresentation?
  @State private var projectPendingDeletion: TaskProjectRecord?
  @State private var isConfirmingClearCompleted = false

  var body: some View {
    NavigationSplitView {
      sidebar
    } detail: {
      NavigationStack(path: $taskPath) {
        taskList
          .navigationDestination(for: String.self) { taskID in
            TaskDetailDestination(taskID: taskID)
          }
      }
    }
    .searchable(text: $searchText, prompt: "Search tasks")
    .sheet(item: $taskEditor) { presentation in
      TaskEditorView(presentation: presentation) { draft in
        saveTask(presentation, draft: draft)
      }
    }
    .sheet(item: $projectEditor) { presentation in
      TaskProjectEditorView(presentation: presentation) { draft in
        saveProject(presentation, draft: draft)
      }
    }
    .alert(
      "Delete project?",
      isPresented: Binding(
        get: { projectPendingDeletion != nil },
        set: { if !$0 { projectPendingDeletion = nil } }
      ),
      presenting: projectPendingDeletion
    ) { project in
      Button("Delete", role: .destructive) {
        _Concurrency.Task {
          await store.deleteProject(projectID: project.id)
          selection = .smart(.inbox)
          projectPendingDeletion = nil
        }
      }
      Button("Cancel", role: .cancel) {
        projectPendingDeletion = nil
      }
    } message: { project in
      Text(
        "Tasks in \(project.name) move to Inbox. The change is saved locally and synchronized later if you are offline."
      )
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
      store.presentedError?.title ?? "Task Operation Failed",
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
    .onChange(of: store.projects) {
      normalizeSelection()
    }
    .onChange(of: store.projectConflicts) {
      normalizeSelection()
    }
    .onChange(of: notificationNavigationState) {
      openNotificationTaskIfAvailable()
    }
  }

  private var sidebar: some View {
    List(selection: $selection) {
      Section("Tasks") {
        ForEach(TaskListFilter.allCases) { option in
          NavigationLink(value: TaskCollectionSelection.smart(option)) {
            Label(option.title, systemImage: option.systemImage)
          }
          .badge(store.count(for: option))
        }
      }

      Section("Projects") {
        if store.projects.isEmpty {
          Text("No projects")
            .foregroundStyle(.secondary)
        } else {
          ForEach(store.projects) { project in
            NavigationLink(value: TaskCollectionSelection.project(project.id)) {
              TaskProjectSidebarRow(
                project: project,
                taskCount: store.count(for: .project(project.id))
              )
            }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
              Button(role: .destructive) {
                projectPendingDeletion = project
              } label: {
                Label("Delete", systemImage: "trash")
              }
              .disabled(project.syncState == .conflict)

              Button {
                projectEditor = TaskProjectEditorPresentation(mode: .edit(project))
              } label: {
                Label("Edit", systemImage: "square.and.pencil")
              }
              .tint(.accentColor)
              .disabled(project.syncState == .conflict)
            }
            .contextMenu {
              projectActions(project)
            }
          }
        }
      }

      if !detachedProjectConflicts.isEmpty {
        Section("Project Conflicts") {
          ForEach(detachedProjectConflicts) { conflict in
            NavigationLink(value: TaskCollectionSelection.project(conflict.id)) {
              Label(
                conflict.displayedProject?.name ?? "Deleted Project",
                systemImage: "exclamationmark.triangle.fill"
              )
              .foregroundStyle(.orange)
            }
          }
        }
      }

      Section {
        SyncStatusView(status: store.syncStatus)
      }
    }
    .navigationTitle("Tasks")
    .toolbar {
      ToolbarItem(placement: .primaryAction) {
        Button {
          projectEditor = TaskProjectEditorPresentation(mode: .create)
        } label: {
          Label("New Project", systemImage: "folder.badge.plus")
        }
      }
    }
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

      if let projectConflict = selectedProjectConflict {
        Section {
          TaskProjectConflictView(
            conflict: projectConflict,
            keepLocal: {
              _Concurrency.Task {
                await store.keepLocalProject(projectID: projectConflict.id)
              }
            },
            useRemote: {
              _Concurrency.Task {
                await store.useRemoteProject(projectID: projectConflict.id)
              }
            },
            merge: {
              projectEditor = TaskProjectEditorPresentation(
                mode: .merge(projectConflict)
              )
            }
          )
          .listRowInsets(EdgeInsets())
          .listRowBackground(Color.clear)
        }
      }

      if visibleTasks.isEmpty {
        ContentUnavailableView(
          emptyTitle,
          systemImage: store.systemImage(for: selectedCollection),
          description: Text(emptyDescription)
        )
        .listRowBackground(Color.clear)
      } else {
        ForEach(visibleTasks) { task in
          NavigationLink(value: task.id) {
            TaskRow(
              task: task,
              project: task.projectID.flatMap {
                store.displayedProject(id: $0)
              }
            ) {
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
    .navigationTitle(store.title(for: selectedCollection))
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
          taskEditor = TaskEditorPresentation(
            mode: .create(projectID: selectedCollection.selectedProjectID)
          )
        } label: {
          Label("New Task", systemImage: "plus")
        }

        Menu {
          Button("New Project", systemImage: "folder.badge.plus") {
            projectEditor = TaskProjectEditorPresentation(mode: .create)
          }

          if let project = selectedProject {
            Divider()
            projectActions(project)
          }

          Divider()
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

  @ViewBuilder
  private func projectActions(_ project: TaskProjectRecord) -> some View {
    Button("Edit Project", systemImage: "square.and.pencil") {
      projectEditor = TaskProjectEditorPresentation(mode: .edit(project))
    }
    .disabled(project.syncState == .conflict)

    Button("Delete Project", systemImage: "trash", role: .destructive) {
      projectPendingDeletion = project
    }
    .disabled(project.syncState == .conflict)
  }

  private var selectedCollection: TaskCollectionSelection {
    selection ?? .smart(.inbox)
  }

  private var selectedProject: TaskProjectRecord? {
    selectedCollection.selectedProjectID.flatMap {
      store.project(id: $0)
    }
  }

  private var selectedProjectConflict: TaskProjectConflictRecord? {
    selectedCollection.selectedProjectID.flatMap {
      store.projectConflict(projectID: $0)
    }
  }

  private var detachedProjectConflicts: [TaskProjectConflictRecord] {
    store.projectConflicts.filter { store.project(id: $0.id) == nil }
  }

  private var notificationNavigationState: TaskNotificationNavigationState {
    let route = notifications.route
    let taskID = route?.taskID
    return TaskNotificationNavigationState(
      route: route,
      isAvailable: taskID.map {
        store.task(id: $0) != nil || store.conflict(taskID: $0) != nil
      } ?? false
    )
  }

  private var visibleTasks: [TaskRecord] {
    store.filteredTasks(selection: selectedCollection, searchText: searchText)
  }

  private var emptyTitle: String {
    searchText.isEmpty
      ? "No \(store.title(for: selectedCollection)) Tasks"
      : "No Results"
  }

  private var emptyDescription: String {
    searchText.isEmpty
      ? "Create a task or select another list."
      : "Try a different title or note."
  }

  private func normalizeSelection() {
    guard let projectID = selectedCollection.selectedProjectID else { return }
    if store.displayedProject(id: projectID) == nil {
      selection = .smart(.inbox)
    }
  }

  private func openNotificationTaskIfAvailable() {
    guard
      let route = notifications.route,
      store.task(id: route.taskID) != nil || store.conflict(taskID: route.taskID) != nil
    else {
      return
    }
    notifications.consumeRoute()
    selection = .smart(.all)
    searchText = ""
    taskPath = [route.taskID]
  }

  private func saveTask(
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

  private func saveProject(
    _ presentation: TaskProjectEditorPresentation,
    draft: TaskProjectEditorDraft
  ) {
    _Concurrency.Task {
      switch presentation.mode {
      case .create:
        await store.createProject(draft)
      case .edit(let project):
        await store.updateProject(projectID: project.id, draft: draft)
      case .merge(let conflict):
        await store.mergeProjectConflict(projectID: conflict.id, draft: draft)
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
    .environment(TaskNotificationCoordinator())
}
