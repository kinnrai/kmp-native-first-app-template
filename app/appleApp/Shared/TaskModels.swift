import Foundation
import SharedLogic

enum TaskListFilter: String, CaseIterable, Identifiable {
  case all
  case active
  case completed
  case conflicts

  var id: Self { self }

  var title: String {
    switch self {
    case .all: "All"
    case .active: "Active"
    case .completed: "Completed"
    case .conflicts: "Conflicts"
    }
  }

  var systemImage: String {
    switch self {
    case .all: "tray.full"
    case .active: "circle"
    case .completed: "checkmark.circle"
    case .conflicts: "exclamationmark.triangle"
    }
  }
}

enum TaskPriorityValue: Int, CaseIterable, Identifiable, Comparable {
  case none
  case low
  case medium
  case high

  var id: Self { self }

  var title: String {
    switch self {
    case .none: "None"
    case .low: "Low"
    case .medium: "Medium"
    case .high: "High"
    }
  }

  var systemImage: String {
    switch self {
    case .none: "minus"
    case .low: "exclamationmark"
    case .medium: "exclamationmark.2"
    case .high: "exclamationmark.3"
    }
  }

  static func < (lhs: Self, rhs: Self) -> Bool {
    lhs.rawValue < rhs.rawValue
  }

  init(_ priority: SharedLogic.TaskPriority) {
    if priority === SharedLogic.TaskPriority.high {
      self = .high
    } else if priority === SharedLogic.TaskPriority.medium {
      self = .medium
    } else if priority === SharedLogic.TaskPriority.low {
      self = .low
    } else {
      self = .none
    }
  }

  var kotlinValue: SharedLogic.TaskPriority {
    switch self {
    case .none: SharedLogic.TaskPriority.none
    case .low: SharedLogic.TaskPriority.low
    case .medium: SharedLogic.TaskPriority.medium
    case .high: SharedLogic.TaskPriority.high
    }
  }
}

enum TaskSyncStateValue: Hashable {
  case synced
  case pending
  case conflict

  init(_ state: SharedLogic.TaskSyncState) {
    if state === SharedLogic.TaskSyncState.conflict {
      self = .conflict
    } else if state === SharedLogic.TaskSyncState.pending {
      self = .pending
    } else {
      self = .synced
    }
  }
}

struct TaskRecord: Identifiable, Hashable {
  let id: String
  let title: String
  let notes: String?
  let priority: TaskPriorityValue
  let dueAt: Date?
  let isCompleted: Bool
  let createdAt: Date
  let updatedAt: Date
  let revision: Int64
  let syncState: TaskSyncStateValue

  init(_ item: SharedLogic.TaskItem) {
    self.init(task: item.task, syncState: TaskSyncStateValue(item.syncState))
  }

  init(
    task: SharedLogic.Task,
    syncState: TaskSyncStateValue
  ) {
    id = task.id
    title = task.title
    notes = task.notes
    priority = TaskPriorityValue(task.priority)
    dueAt = task.dueAt?.date
    isCompleted = task.isCompleted
    createdAt = task.createdAt.date
    updatedAt = task.updatedAt.date
    revision = task.revision
    self.syncState = syncState
  }
}

struct TaskEditorDraft: Equatable {
  var title = ""
  var notes = ""
  var priority = TaskPriorityValue.none
  var includesDueDate = false
  var dueAt = Date()
  var isCompleted = false

  init(task: TaskRecord? = nil) {
    guard let task else { return }
    title = task.title
    notes = task.notes ?? ""
    priority = task.priority
    includesDueDate = task.dueAt != nil
    dueAt = task.dueAt ?? Date()
    isCompleted = task.isCompleted
  }

  var normalizedTitle: String {
    title.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  var normalizedNotes: String? {
    let value = notes.trimmingCharacters(in: .whitespacesAndNewlines)
    return value.isEmpty ? nil : value
  }

  var isValid: Bool {
    !normalizedTitle.isEmpty && normalizedTitle.count <= 200 && notes.count <= 4_000
  }
}

struct TaskConflictRecord: Identifiable, Hashable {
  let id: String
  let local: TaskRecord?
  let remote: TaskRecord?
  let fields: [String]
  let detectedAt: Date

  init(_ conflict: SharedLogic.TaskConflict) {
    id = conflict.taskId
    local = conflict.local.map {
      TaskRecord(task: $0, syncState: .conflict)
    }
    remote = conflict.remote.map {
      TaskRecord(task: $0, syncState: .synced)
    }
    fields = conflict.conflictingFields
      .map { TaskConflictFieldValue($0).title }
      .sorted()
    detectedAt = conflict.detectedAt.date
  }
}

private enum TaskConflictFieldValue {
  case creation
  case deletion
  case title
  case notes
  case priority
  case dueDate
  case completion

  init(_ field: SharedLogic.TaskConflictField) {
    if field === SharedLogic.TaskConflictField.creation {
      self = .creation
    } else if field === SharedLogic.TaskConflictField.deletion {
      self = .deletion
    } else if field === SharedLogic.TaskConflictField.title {
      self = .title
    } else if field === SharedLogic.TaskConflictField.notes {
      self = .notes
    } else if field === SharedLogic.TaskConflictField.priority {
      self = .priority
    } else if field === SharedLogic.TaskConflictField.dueDate ||
                field === SharedLogic.TaskConflictField.dueAt {
      self = .dueDate
    } else {
      self = .completion
    }
  }

  var title: String {
    switch self {
    case .creation: "creation"
    case .deletion: "deletion"
    case .title: "title"
    case .notes: "notes"
    case .priority: "priority"
    case .dueDate: "due date"
    case .completion: "completion"
    }
  }
}

enum TaskSyncPhaseValue: Hashable {
  case idle
  case syncing
  case failed

  init(_ phase: SharedLogic.TaskSyncPhase) {
    if phase === SharedLogic.TaskSyncPhase.syncing {
      self = .syncing
    } else if phase === SharedLogic.TaskSyncPhase.failed {
      self = .failed
    } else {
      self = .idle
    }
  }
}

struct TaskSyncStatusValue: Equatable {
  let phase: TaskSyncPhaseValue
  let pendingCount: Int
  let conflictCount: Int
  let lastSyncedAt: Date?
  let lastError: String?

  static let initial = Self(
    phase: .idle,
    pendingCount: 0,
    conflictCount: 0,
    lastSyncedAt: nil,
    lastError: nil
  )

  init(_ status: SharedLogic.TaskSyncStatus) {
    phase = TaskSyncPhaseValue(status.phase)
    pendingCount = Int(status.pendingCount)
    conflictCount = Int(status.conflictCount)
    lastSyncedAt = status.lastSyncedAt?.date
    lastError = status.lastError?.message
  }

  private init(
    phase: TaskSyncPhaseValue,
    pendingCount: Int,
    conflictCount: Int,
    lastSyncedAt: Date?,
    lastError: String?
  ) {
    self.phase = phase
    self.pendingCount = pendingCount
    self.conflictCount = conflictCount
    self.lastSyncedAt = lastSyncedAt
    self.lastError = lastError
  }
}

extension SharedLogic.KotlinInstant {
  var date: Date {
    Date(timeIntervalSince1970: TimeInterval(toEpochMilliseconds()) / 1_000)
  }
}

extension Date {
  var kotlinInstant: SharedLogic.KotlinInstant {
    SharedLogic.KotlinInstant.companion.fromEpochMilliseconds(
      epochMilliseconds: Int64((timeIntervalSince1970 * 1_000).rounded())
    )
  }
}
