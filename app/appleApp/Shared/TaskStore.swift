import Foundation
import Observation
import SharedLogic

@MainActor
@Observable
final class TaskStore {
  private(set) var tasks: [TaskRecord] = []
  private(set) var conflicts: [TaskConflictRecord] = []
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
    switch filter {
    case .all:
      tasks.count
    case .active:
      tasks.count { !$0.isCompleted }
    case .completed:
      completedCount
    case .conflicts:
      conflicts.count
    }
  }

  func task(id: String) -> TaskRecord? {
    tasks.first { $0.id == id }
  }

  func conflict(taskID: String) -> TaskConflictRecord? {
    conflicts.first { $0.id == taskID }
  }

  func filteredTasks(
    filter: TaskListFilter,
    searchText: String
  ) -> [TaskRecord] {
    let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    let candidates =
      filter == .conflicts
      ? conflicts.compactMap { $0.local ?? $0.remote }
      : tasks
    return
      candidates
      .filter { task in
        switch filter {
        case .all:
          true
        case .active:
          !task.isCompleted
        case .completed:
          task.isCompleted
        case .conflicts:
          true
        }
      }
      .filter { task in
        query.isEmpty || task.title.localizedStandardContains(query)
          || task.notes?.localizedStandardContains(query) == true
      }
      .sorted { lhs, rhs in
        if lhs.isCompleted != rhs.isCompleted {
          return !lhs.isCompleted
        }
        if lhs.priority != rhs.priority {
          return lhs.priority > rhs.priority
        }
        switch (lhs.dueAt, rhs.dueAt) {
        case (let left?, let right?) where left != right:
          return left < right
        case (_?, nil):
          return true
        case (nil, _?):
          return false
        default:
          return lhs.updatedAt > rhs.updatedAt
        }
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
        dueAt: draft.includesDueDate ? draft.dueAt.kotlinInstant : nil
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
        dueAt: draft.includesDueDate ? draft.dueAt.kotlinInstant : nil,
        isCompleted: draft.isCompleted
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
        dueAt: draft.includesDueDate ? draft.dueAt.kotlinInstant : nil,
        isCompleted: draft.isCompleted
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
    syncStatus = TaskSyncStatusValue(snapshot.syncStatus)
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
