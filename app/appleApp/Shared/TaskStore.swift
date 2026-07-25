import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class TaskStore {
  private(set) var tasks: [TaskRecord] = []
  private(set) var conflicts: [TaskConflictRecord] = []
  private(set) var projects: [TaskProjectRecord] = []
  private(set) var projectConflicts: [TaskProjectConflictRecord] = []
  private(set) var syncStatus = TaskSyncStatusValue.initial
  private(set) var isStarting = false
  private(set) var activeOperationCount = 0
  var presentedError: PresentedTaskError?

  @ObservationIgnored private let baseURL: URL
  @ObservationIgnored private let databaseName: String
  @ObservationIgnored private var backend: AppleTaskStore?
  @ObservationIgnored private var observation: AppleTaskObservation?
  @ObservationIgnored private var hasStarted = false

  init(
    baseURL: URL = AppConfiguration.taskAPIBaseURL,
    databaseName: String = "tasks.db"
  ) {
    self.baseURL = baseURL
    self.databaseName = databaseName
  }

  deinit {
    observation?.cancel()
    try? backend?.close()
  }

  var isBusy: Bool {
    isStarting || activeOperationCount > 0 || syncStatus.phase == .syncing
  }

  var completedCount: Int {
    tasks.count(where: \.isCompleted)
  }

  var clearableCompletedCount: Int {
    tasks.count { $0.isCompleted && $0.syncState != .conflict }
  }

  func count(for filter: TaskListFilter) -> Int {
    filter == .conflicts ? conflicts.count : plannedTasks(for: filter).count
  }

  func task(id: String) -> TaskRecord? {
    tasks.first { $0.id == id }
  }

  func conflict(taskID: String) -> TaskConflictRecord? {
    conflicts.first { $0.id == taskID }
  }

  func project(id: String) -> TaskProjectRecord? {
    projects.first { $0.id == id }
  }

  func projectConflict(projectID: String) -> TaskProjectConflictRecord? {
    projectConflicts.first { $0.id == projectID }
  }

  func displayedProject(id: String) -> TaskProjectRecord? {
    project(id: id) ?? projectConflict(projectID: id)?.displayedProject
  }

  func filteredTasks(
    filter: TaskListFilter,
    searchText: String
  ) -> [TaskRecord] {
    let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    let candidates =
      filter == .conflicts
      ? conflicts.compactMap { $0.local ?? $0.remote }
      : plannedTasks(for: filter)
    return
      candidates
      .filter { task in
        query.isEmpty || task.title.localizedStandardContains(query)
          || task.notes?.localizedStandardContains(query) == true
      }
  }

  func filteredTasks(
    selection: TaskCollectionSelection,
    searchText: String
  ) -> [TaskRecord] {
    switch selection {
    case .smart(let filter):
      filteredTasks(filter: filter, searchText: searchText)
    case .project(let projectID):
      filterTasks(
        tasks.filter { $0.projectID == projectID },
        searchText: searchText
      )
    }
  }

  func title(for selection: TaskCollectionSelection) -> String {
    switch selection {
    case .smart(let filter):
      filter.title
    case .project(let projectID):
      displayedProject(id: projectID)?.name ?? "Project"
    }
  }

  func systemImage(for selection: TaskCollectionSelection) -> String {
    switch selection {
    case .smart(let filter):
      filter.systemImage
    case .project:
      "folder"
    }
  }

  func count(for selection: TaskCollectionSelection) -> Int {
    switch selection {
    case .smart(let filter):
      count(for: filter)
    case .project(let projectID):
      tasks.count { $0.projectID == projectID }
    }
  }

  func start() async {
    guard !hasStarted else { return }
    hasStarted = true
    isStarting = true
    defer { isStarting = false }

    do {
      let backend = try await AppleTaskStoreFactoryKt.createAppleTaskStore(
        baseUrl: baseURL.absoluteString,
        databaseName: databaseName
      )
      self.backend = backend
      observation = backend.observe { [weak self] snapshot in
        _Concurrency.Task { @MainActor in
          self?.apply(snapshot)
        }
      }
      await sync()
    } catch {
      hasStarted = false
      present(error)
    }
  }

  func create(_ draft: TaskEditorDraft) async {
    guard let backend else { return }
    await perform {
      _ = try await backend.create(
        title: draft.normalizedTitle,
        notes: draft.normalizedNotes,
        priority: draft.priority.kotlinValue,
        dueDate: draft.dueDate,
        dueAt: draft.dueAt,
        reminderAt: draft.kotlinReminderAt,
        projectId: draft.projectID
      )
    }
  }

  func update(
    taskID: String,
    draft: TaskEditorDraft
  ) async {
    guard let backend else { return }
    await perform {
      _ = try await backend.update(
        taskId: taskID,
        title: draft.normalizedTitle,
        notes: draft.normalizedNotes,
        priority: draft.priority.kotlinValue,
        dueDate: draft.dueDate,
        dueAt: draft.dueAt,
        reminderAt: draft.kotlinReminderAt,
        isCompleted: draft.isCompleted,
        projectId: draft.projectID
      )
    }
  }

  func toggleCompleted(taskID: String) async {
    guard let backend else { return }
    await perform {
      _ = try await backend.toggleCompleted(taskId: taskID)
    }
  }

  func delete(taskID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.delete(taskId: taskID)
    }
  }

  func clearCompleted() async {
    guard let backend else { return }
    await perform {
      try await backend.clearCompleted()
    }
  }

  func keepLocal(taskID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.keepLocal(taskId: taskID)
    }
  }

  func useRemote(taskID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.useRemote(taskId: taskID)
    }
  }

  func mergeConflict(
    taskID: String,
    draft: TaskEditorDraft
  ) async {
    guard let backend else { return }
    await perform {
      try await backend.mergeConflict(
        taskId: taskID,
        title: draft.normalizedTitle,
        notes: draft.normalizedNotes,
        priority: draft.priority.kotlinValue,
        dueDate: draft.dueDate,
        dueAt: draft.dueAt,
        reminderAt: draft.kotlinReminderAt,
        isCompleted: draft.isCompleted,
        projectId: draft.projectID
      )
    }
  }

  func createProject(_ draft: TaskProjectEditorDraft) async {
    guard let backend else { return }
    await perform {
      _ = try await backend.createProject(
        name: draft.normalizedName,
        color: draft.color.kotlinValue
      )
    }
  }

  func updateProject(
    projectID: String,
    draft: TaskProjectEditorDraft
  ) async {
    guard let backend else { return }
    await perform {
      _ = try await backend.updateProject(
        projectId: projectID,
        name: draft.normalizedName,
        color: draft.color.kotlinValue
      )
    }
  }

  func deleteProject(projectID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.deleteProject(projectId: projectID)
    }
  }

  func keepLocalProject(projectID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.keepLocalProject(projectId: projectID)
    }
  }

  func useRemoteProject(projectID: String) async {
    guard let backend else { return }
    await perform {
      try await backend.useRemoteProject(projectId: projectID)
    }
  }

  func mergeProjectConflict(
    projectID: String,
    draft: TaskProjectEditorDraft
  ) async {
    guard let backend else { return }
    await perform {
      try await backend.mergeProjectConflict(
        projectId: projectID,
        name: draft.normalizedName,
        color: draft.color.kotlinValue
      )
    }
  }

  func sync() async {
    guard let backend else { return }
    await perform {
      _ = try await backend.sync()
    }
  }

  func dismissError() {
    presentedError = nil
  }

  private func apply(_ snapshot: AppleTaskSnapshot) {
    tasks = snapshot.tasks.map(TaskRecord.init)
    conflicts = snapshot.conflicts.map(TaskConflictRecord.init)
    projects = snapshot.projects.map(TaskProjectRecord.init)
    projectConflicts = snapshot.projectConflicts.map(TaskProjectConflictRecord.init)
    syncStatus = TaskSyncStatusValue(snapshot.syncStatus)
  }

  private func filterTasks(
    _ tasks: [TaskRecord],
    searchText: String
  ) -> [TaskRecord] {
    let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !query.isEmpty else { return tasks }
    return tasks.filter { task in
      task.title.localizedStandardContains(query)
        || task.notes?.localizedStandardContains(query) == true
    }
  }

  private func plannedTasks(for filter: TaskListFilter) -> [TaskRecord] {
    guard let backend, let view = filter.kotlinValue else { return tasks }
    let calendar = Calendar.current
    let today = calendar.dateComponents([.year, .month, .day], from: Date())
    return backend.plannedTasks(
      view: view,
      todayYear: Int32(today.year!),
      todayMonth: Int32(today.month!),
      todayDay: Int32(today.day!),
      timeZoneId: calendar.timeZone.identifier
    ).map(TaskRecord.init)
  }

  private func perform(
    _ operation: () async throws -> Void
  ) async {
    activeOperationCount += 1
    defer { activeOperationCount -= 1 }
    do {
      try await operation()
    } catch {
      present(error)
    }
  }

  private func present(_ error: Error) {
    presentedError = PresentedTaskError(message: error.localizedDescription)
  }
}

struct PresentedTaskError: Identifiable {
  let id = UUID()
  let message: String
}
