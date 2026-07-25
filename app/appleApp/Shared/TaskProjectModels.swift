import Foundation
import SharedLogic

enum TaskProjectColorValue: String, CaseIterable, Identifiable {
  case blue
  case green
  case orange
  case purple
  case rose
  case slate

  var id: Self { self }

  var title: String {
    rawValue.capitalized
  }

  init(_ color: SharedLogic.TaskProjectColor) {
    if color === SharedLogic.TaskProjectColor.green {
      self = .green
    } else if color === SharedLogic.TaskProjectColor.orange {
      self = .orange
    } else if color === SharedLogic.TaskProjectColor.purple {
      self = .purple
    } else if color === SharedLogic.TaskProjectColor.rose {
      self = .rose
    } else if color === SharedLogic.TaskProjectColor.slate {
      self = .slate
    } else {
      self = .blue
    }
  }

  var kotlinValue: SharedLogic.TaskProjectColor {
    switch self {
    case .blue: SharedLogic.TaskProjectColor.blue
    case .green: SharedLogic.TaskProjectColor.green
    case .orange: SharedLogic.TaskProjectColor.orange
    case .purple: SharedLogic.TaskProjectColor.purple
    case .rose: SharedLogic.TaskProjectColor.rose
    case .slate: SharedLogic.TaskProjectColor.slate
    }
  }
}

struct TaskProjectRecord: Identifiable, Hashable {
  let id: String
  let name: String
  let color: TaskProjectColorValue
  let createdAt: Date
  let updatedAt: Date
  let revision: Int64
  let syncState: TaskSyncStateValue

  init(_ item: SharedLogic.TaskProjectItem) {
    self.init(
      project: item.project,
      syncState: TaskSyncStateValue(item.syncState)
    )
  }

  init(
    project: SharedLogic.TaskProject,
    syncState: TaskSyncStateValue
  ) {
    id = project.id
    name = project.name
    color = TaskProjectColorValue(project.color)
    createdAt = project.createdAt.date
    updatedAt = project.updatedAt.date
    revision = project.revision
    self.syncState = syncState
  }
}

struct TaskProjectEditorDraft: Equatable {
  var name = ""
  var color = TaskProjectColorValue.blue

  init(project: TaskProjectRecord? = nil) {
    guard let project else { return }
    name = project.name
    color = project.color
  }

  var normalizedName: String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  var isValid: Bool {
    !normalizedName.isEmpty && normalizedName.count <= 80
  }
}

struct TaskProjectConflictRecord: Identifiable, Hashable {
  let id: String
  let local: TaskProjectRecord?
  let remote: TaskProjectRecord?
  let fields: [String]
  let detectedAt: Date

  init(_ conflict: SharedLogic.TaskProjectConflict) {
    id = conflict.projectId
    local = conflict.local.map {
      TaskProjectRecord(project: $0, syncState: .conflict)
    }
    remote = conflict.remote.map {
      TaskProjectRecord(project: $0, syncState: .synced)
    }
    fields = conflict.conflictingFields
      .map { TaskProjectConflictFieldValue($0).title }
      .sorted()
    detectedAt = conflict.detectedAt.date
  }

  var displayedProject: TaskProjectRecord? {
    local ?? remote
  }
}

private enum TaskProjectConflictFieldValue {
  case creation
  case deletion
  case name
  case color

  init(_ field: SharedLogic.TaskProjectConflictField) {
    if field === SharedLogic.TaskProjectConflictField.creation {
      self = .creation
    } else if field === SharedLogic.TaskProjectConflictField.deletion {
      self = .deletion
    } else if field === SharedLogic.TaskProjectConflictField.name {
      self = .name
    } else {
      self = .color
    }
  }

  var title: String {
    switch self {
    case .creation: "creation"
    case .deletion: "deletion"
    case .name: "name"
    case .color: "color"
    }
  }
}

enum TaskCollectionSelection: Hashable {
  case smart(TaskListFilter)
  case project(String)

  var selectedProjectID: String? {
    guard case .project(let projectID) = self else { return nil }
    return projectID
  }
}
