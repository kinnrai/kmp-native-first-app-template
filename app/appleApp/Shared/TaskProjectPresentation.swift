import SwiftUI

extension TaskProjectColorValue {
  var color: Color {
    switch self {
    case .blue: .blue
    case .green: .green
    case .orange: .orange
    case .purple: .purple
    case .rose: .pink
    case .slate: .gray
    }
  }
}

struct TaskProjectEditorPresentation: Identifiable {
  enum Mode {
    case create
    case edit(TaskProjectRecord)
    case merge(TaskProjectConflictRecord)
  }

  let id = UUID()
  let mode: Mode

  var navigationTitle: String {
    switch mode {
    case .create: "New Project"
    case .edit: "Edit Project"
    case .merge: "Resolve Project Conflict"
    }
  }

  var draft: TaskProjectEditorDraft {
    switch mode {
    case .create:
      TaskProjectEditorDraft()
    case .edit(let project):
      TaskProjectEditorDraft(project: project)
    case .merge(let conflict):
      TaskProjectEditorDraft(project: conflict.local ?? conflict.remote)
    }
  }
}

struct TaskProjectLabel: View {
  let project: TaskProjectRecord

  var body: some View {
    Label {
      Text(project.name)
    } icon: {
      Image(systemName: "circle.fill")
        .foregroundStyle(project.color.color)
    }
  }
}

struct TaskProjectSidebarRow: View {
  let project: TaskProjectRecord
  let taskCount: Int

  var body: some View {
    TaskProjectLabel(project: project)
      .badge(taskCount)
      .overlay(alignment: .trailing) {
        if project.syncState == .conflict {
          Image(systemName: "exclamationmark.triangle.fill")
            .foregroundStyle(.orange)
            .accessibilityLabel("Synchronization conflict")
        } else if project.syncState == .pending {
          Image(systemName: "arrow.up.circle")
            .foregroundStyle(.secondary)
            .accessibilityLabel("Pending synchronization")
        }
      }
  }
}
