import Observation
import SwiftUI

@MainActor
@Observable
final class MacTaskCommandModel {
  var collectionSelection: TaskCollectionSelection? = .smart(.inbox)
  var selectedTaskID: String?
  var newTaskRequest = UUID()
  var newProjectRequest = UUID()
  var editProjectRequest = UUID()
  var deleteProjectRequest = UUID()
  var clearCompletedRequest = UUID()

  func requestNewTask() {
    newTaskRequest = UUID()
  }

  func requestNewProject() {
    newProjectRequest = UUID()
  }

  func requestEditProject() {
    editProjectRequest = UUID()
  }

  func requestDeleteProject() {
    deleteProjectRequest = UUID()
  }

  func requestClearCompleted() {
    clearCompletedRequest = UUID()
  }
}

struct ContentView: View {
  @Environment(TaskStore.self) private var store
  @Environment(MacTaskCommandModel.self) private var commands

  @State private var searchText = ""
  @State private var taskEditor: TaskEditorPresentation?
  @State private var projectEditor: TaskProjectEditorPresentation?
  @State private var projectPendingDeletion: TaskProjectRecord?
  @State private var isConfirmingClearCompleted = false

  var body: some View {
    @Bindable var commands = commands

    NavigationSplitView {
      sidebar(selection: $commands.collectionSelection)
    } content: {
      List(selection: $commands.selectedTaskID) {
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
            searchText.isEmpty
              ? "No \(store.title(for: selectedCollection)) Tasks"
              : "No Results",
            systemImage: store.systemImage(for: selectedCollection),
            description: Text(
              searchText.isEmpty
                ? "Create a task or select another list."
                : "Try a different title or note."
            )
          )
          .listRowBackground(Color.clear)
        } else {
          ForEach(visibleTasks) { task in
            taskRow(task)
          }
        }
      }
      .navigationTitle(store.title(for: selectedCollection))
      .navigationSplitViewColumnWidth(min: 280, ideal: 340)
      .searchable(text: $searchText, prompt: "Search tasks")
    } detail: {
      Group {
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
      .navigationSplitViewColumnWidth(min: 300, ideal: 440)
    }
    .frame(minWidth: 820, minHeight: 560)
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
          presentNewTask()
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
        } label: {
          Label("Project Actions", systemImage: "folder")
        }
      }
    }
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
          commands.collectionSelection = .smart(.inbox)
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
      presentNewTask()
    }
    .onChange(of: commands.newProjectRequest) {
      projectEditor = TaskProjectEditorPresentation(mode: .create)
    }
    .onChange(of: commands.editProjectRequest) {
      if let project = selectedProject, project.syncState != .conflict {
        projectEditor = TaskProjectEditorPresentation(mode: .edit(project))
      }
    }
    .onChange(of: commands.deleteProjectRequest) {
      if let project = selectedProject, project.syncState != .conflict {
        projectPendingDeletion = project
      }
    }
    .onChange(of: commands.clearCompletedRequest) {
      isConfirmingClearCompleted = true
    }
    .onChange(of: commands.collectionSelection) {
      commands.selectedTaskID = nil
      normalizeSelection()
    }
    .onChange(of: store.projects) {
      normalizeSelection()
    }
    .onChange(of: store.projectConflicts) {
      normalizeSelection()
    }
  }

  private func sidebar(
    selection: Binding<TaskCollectionSelection?>
  ) -> some View {
    List(selection: selection) {
      Section("Tasks") {
        ForEach(TaskListFilter.allCases) { option in
          Label(option.title, systemImage: option.systemImage)
            .badge(store.count(for: option))
            .tag(TaskCollectionSelection.smart(option))
        }
      }

      Section("Projects") {
        if store.projects.isEmpty {
          Text("No projects")
            .foregroundStyle(.secondary)
        } else {
          ForEach(store.projects) { project in
            TaskProjectSidebarRow(
              project: project,
              taskCount: store.count(for: .project(project.id))
            )
            .tag(TaskCollectionSelection.project(project.id))
            .contextMenu {
              projectActions(project)
            }
          }
        }
      }

      if !detachedProjectConflicts.isEmpty {
        Section("Project Conflicts") {
          ForEach(detachedProjectConflicts) { conflict in
            Label(
              conflict.displayedProject?.name ?? "Deleted Project",
              systemImage: "exclamationmark.triangle.fill"
            )
            .foregroundStyle(.orange)
            .tag(TaskCollectionSelection.project(conflict.id))
          }
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
    .navigationSplitViewColumnWidth(min: 190, ideal: 230)
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
    commands.collectionSelection ?? .smart(.inbox)
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

  private var visibleTasks: [TaskRecord] {
    store.filteredTasks(selection: selectedCollection, searchText: searchText)
  }

  private func taskRow(_ task: TaskRecord) -> some View {
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

  private func presentNewTask() {
    taskEditor = TaskEditorPresentation(
      mode: .create(projectID: selectedCollection.selectedProjectID)
    )
  }

  private func normalizeSelection() {
    guard let projectID = selectedCollection.selectedProjectID else { return }
    if store.displayedProject(id: projectID) == nil {
      commands.collectionSelection = .smart(.inbox)
    }
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

struct MacTaskCommands: Commands {
  let store: TaskStore
  let commands: MacTaskCommandModel

  var body: some Commands {
    CommandGroup(replacing: .newItem) {
      Button("New Task") {
        commands.requestNewTask()
      }
      .keyboardShortcut("n", modifiers: .command)

      Button("New Project") {
        commands.requestNewProject()
      }
      .keyboardShortcut("n", modifiers: [.command, .shift])
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

    CommandMenu("Projects") {
      Button("Edit Project") {
        commands.requestEditProject()
      }
      .disabled(!canModifySelectedProject)

      Button("Delete Project") {
        commands.requestDeleteProject()
      }
      .disabled(!canModifySelectedProject)
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

  private var canModifySelectedProject: Bool {
    guard
      case .project(let projectID) = commands.collectionSelection,
      let project = store.project(id: projectID)
    else {
      return false
    }
    return project.syncState != .conflict
  }
}

#Preview {
  ContentView()
    .environment(TaskStore(baseURL: URL(string: "http://127.0.0.1:8080")!))
    .environment(MacTaskCommandModel())
}
