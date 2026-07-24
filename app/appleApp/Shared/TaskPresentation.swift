import SwiftUI

extension TaskPriorityValue {
  var color: Color {
    switch self {
    case .none: .secondary
    case .low: .blue
    case .medium: .orange
    case .high: .red
    }
  }
}

struct TaskEditorPresentation: Identifiable {
  enum Mode {
    case create
    case edit(TaskRecord)
    case merge(TaskConflictRecord)
  }

  let id = UUID()
  let mode: Mode

  var navigationTitle: String {
    switch mode {
    case .create: "New Task"
    case .edit: "Edit Task"
    case .merge: "Resolve Conflict"
    }
  }

  var draft: TaskEditorDraft {
    switch mode {
    case .create:
      TaskEditorDraft()
    case .edit(let task):
      TaskEditorDraft(task: task)
    case .merge(let conflict):
      TaskEditorDraft(task: conflict.local ?? conflict.remote)
    }
  }
}

struct TaskRow: View {
  let task: TaskRecord
  let toggleCompleted: () -> Void

  var body: some View {
    HStack(spacing: 12) {
      Button(action: toggleCompleted) {
        Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
          .font(.title3)
          .foregroundStyle(task.isCompleted ? Color.accentColor : .secondary)
          .contentTransition(.symbolEffect(.replace))
      }
      .buttonStyle(.plain)
      .disabled(task.syncState == .conflict)
      .accessibilityLabel(
        task.isCompleted ? "Mark \(task.title) active" : "Mark \(task.title) completed"
      )

      VStack(alignment: .leading, spacing: 4) {
        Text(task.title)
          .strikethrough(task.isCompleted)
          .foregroundStyle(task.isCompleted ? .secondary : .primary)
          .lineLimit(2)

        HStack(spacing: 8) {
          if task.priority != .none {
            Label(task.priority.title, systemImage: task.priority.systemImage)
              .foregroundStyle(task.priority.color)
          }
          if let dueAt = task.dueAt {
            Label {
              Text(dueAt, format: .dateTime.month(.abbreviated).day())
            } icon: {
              Image(systemName: "calendar")
            }
            .foregroundStyle(dueAt < Date() && !task.isCompleted ? .red : .secondary)
          }
          switch task.syncState {
          case .synced:
            EmptyView()
          case .pending:
            Label("Pending", systemImage: "arrow.trianglehead.2.clockwise.rotate.90")
              .foregroundStyle(.secondary)
          case .conflict:
            Label("Conflict", systemImage: "exclamationmark.triangle.fill")
              .foregroundStyle(.orange)
          }
        }
        .font(.caption)
        .labelStyle(.titleAndIcon)
      }

      Spacer(minLength: 0)
    }
    .padding(.vertical, 3)
    .contentShape(.rect)
  }
}

struct SyncStatusView: View {
  let status: TaskSyncStatusValue

  var body: some View {
    HStack(spacing: 8) {
      switch status.phase {
      case .syncing:
        ProgressView()
          .controlSize(.small)
        Text("Syncing")
      case .failed:
        Image(systemName: "wifi.exclamationmark")
          .foregroundStyle(.red)
        Text("Offline")
      case .idle:
        if status.conflictCount > 0 {
          Image(systemName: "exclamationmark.triangle.fill")
            .foregroundStyle(.orange)
          Text("\(status.conflictCount) conflict\(status.conflictCount == 1 ? "" : "s")")
        } else if status.pendingCount > 0 {
          Image(systemName: "arrow.up.circle")
            .foregroundStyle(.secondary)
          Text("\(status.pendingCount) pending")
        } else {
          Image(systemName: "checkmark.icloud")
            .foregroundStyle(.green)
          Text("Up to date")
        }
      }
    }
    .font(.caption)
    .foregroundStyle(.secondary)
  }
}

struct TaskSyncBadge: View {
  let state: TaskSyncStateValue

  var body: some View {
    switch state {
    case .synced:
      Label("Synced", systemImage: "checkmark.icloud")
        .foregroundStyle(.secondary)
    case .pending:
      Label("Pending synchronization", systemImage: "arrow.up.circle")
        .foregroundStyle(.secondary)
    case .conflict:
      Label("Needs conflict resolution", systemImage: "exclamationmark.triangle.fill")
        .foregroundStyle(.orange)
    }
  }
}
