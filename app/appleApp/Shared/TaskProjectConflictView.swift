import SwiftUI

struct TaskProjectConflictView: View {
  let conflict: TaskProjectConflictRecord
  let keepLocal: () -> Void
  let useRemote: () -> Void
  let merge: () -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 16) {
      Label(
        "This project changed in two places",
        systemImage: "exclamationmark.triangle.fill"
      )
      .font(.headline)
      .foregroundStyle(.orange)

      if !conflict.fields.isEmpty {
        Text("Conflicting fields: \(conflict.fields.formatted())")
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }

      ViewThatFits(in: .horizontal) {
        HStack(alignment: .top, spacing: 12) {
          versionCard(title: "This device", project: conflict.local)
          versionCard(title: "Service", project: conflict.remote)
        }

        VStack(spacing: 12) {
          versionCard(title: "This device", project: conflict.local)
          versionCard(title: "Service", project: conflict.remote)
        }
      }

      ViewThatFits(in: .horizontal) {
        HStack {
          Button("Keep This Device", action: keepLocal)
          Button("Use Service", action: useRemote)
          Spacer()
          Button("Review and Merge", action: merge)
            .buttonStyle(.borderedProminent)
            .disabled(conflict.local == nil && conflict.remote == nil)
        }

        VStack(alignment: .leading) {
          Button("Keep This Device", action: keepLocal)
          Button("Use Service", action: useRemote)
          Button("Review and Merge", action: merge)
            .buttonStyle(.borderedProminent)
            .disabled(conflict.local == nil && conflict.remote == nil)
        }
      }
    }
    .padding()
    .background(.orange.opacity(0.08), in: .rect(cornerRadius: 14))
  }

  private func versionCard(
    title: String,
    project: TaskProjectRecord?
  ) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(title)
        .font(.caption.weight(.semibold))
        .foregroundStyle(.secondary)

      if let project {
        TaskProjectLabel(project: project)
          .font(.headline)
        TaskSyncBadge(state: project.syncState)
          .font(.caption)
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
