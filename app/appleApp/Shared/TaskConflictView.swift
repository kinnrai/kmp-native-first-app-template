import SwiftUI

struct TaskConflictView: View {
  let conflict: TaskConflictRecord
  let keepLocal: () -> Void
  let useRemote: () -> Void
  let merge: () -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 16) {
      Label("This task changed in two places", systemImage: "exclamationmark.triangle.fill")
        .font(.headline)
        .foregroundStyle(.orange)

      if !conflict.fields.isEmpty {
        Text("Conflicting fields: \(conflict.fields.formatted())")
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }

      ViewThatFits(in: .horizontal) {
        HStack(alignment: .top, spacing: 12) {
          versionCard(title: "This device", task: conflict.local)
          versionCard(title: "Service", task: conflict.remote)
        }

        VStack(spacing: 12) {
          versionCard(title: "This device", task: conflict.local)
          versionCard(title: "Service", task: conflict.remote)
        }
      }

      HStack {
        Button("Keep This Device", action: keepLocal)
        Button("Use Service", action: useRemote)
        Spacer()
        Button("Review and Merge", action: merge)
          .buttonStyle(.borderedProminent)
          .disabled(conflict.local == nil && conflict.remote == nil)
      }
    }
    .padding()
    .background(.orange.opacity(0.08), in: .rect(cornerRadius: 14))
  }

  private func versionCard(
    title: String,
    task: TaskRecord?
  ) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(title)
        .font(.caption.weight(.semibold))
        .foregroundStyle(.secondary)

      if let task {
        Text(task.title)
          .font(.headline)
        if let notes = task.notes {
          Text(notes)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .lineLimit(4)
        }
        LabeledContent("Priority", value: task.priority.title)
        LabeledContent(
          "Status",
          value: task.isCompleted ? "Completed" : "Active"
        )
      } else {
        ContentUnavailableView(
          "Deleted",
          systemImage: "trash",
          description: Text("This version no longer exists.")
        )
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding()
    .background(.background, in: .rect(cornerRadius: 10))
  }
}
