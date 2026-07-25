import SwiftUI

struct TaskDetailView: View {
  let taskID: String
  var deleted: () -> Void = {}

  @Environment(TaskStore.self) private var store
  @State private var editor: TaskEditorPresentation?
  @State private var isConfirmingDeletion = false

  var body: some View {
    Group {
      if let task = displayedTask {
        ScrollView {
          VStack(alignment: .leading, spacing: 24) {
            taskHeader(task)

            if let conflict = store.conflict(taskID: taskID) {
              TaskConflictView(
                conflict: conflict,
                keepLocal: {
                  _Concurrency.Task {
                    await store.keepLocal(taskID: taskID)
                  }
                },
                useRemote: {
                  _Concurrency.Task {
                    await store.useRemote(taskID: taskID)
                  }
                },
                merge: {
                  editor = TaskEditorPresentation(mode: .merge(conflict))
                }
              )
            }

            details(task)
          }
          .frame(maxWidth: 720, alignment: .leading)
          .padding()
        }
        .navigationTitle(task.title)
        #if os(iOS)
          .navigationBarTitleDisplayMode(.inline)
        #endif
        .toolbar {
          ToolbarItemGroup(placement: .primaryAction) {
            Button {
              _Concurrency.Task {
                await store.toggleCompleted(taskID: taskID)
              }
            } label: {
              Label(
                task.isCompleted ? "Mark Active" : "Complete",
                systemImage: task.isCompleted ? "arrow.uturn.backward.circle" : "checkmark.circle"
              )
            }
            .disabled(task.syncState == .conflict)

            Button {
              editor = TaskEditorPresentation(mode: .edit(task))
            } label: {
              Label("Edit", systemImage: "square.and.pencil")
            }
            .disabled(task.syncState == .conflict)

            Button(role: .destructive) {
              isConfirmingDeletion = true
            } label: {
              Label("Delete", systemImage: "trash")
            }
            .disabled(task.syncState == .conflict)
          }
        }
      } else {
        ContentUnavailableView(
          "Task Not Available",
          systemImage: "checklist",
          description: Text("It may have been deleted on another device.")
        )
      }
    }
    .sheet(item: $editor) { presentation in
      TaskEditorView(presentation: presentation) { draft in
        save(presentation, draft: draft)
      }
    }
    .alert("Delete this task?", isPresented: $isConfirmingDeletion) {
      Button("Delete", role: .destructive) {
        _Concurrency.Task {
          await store.delete(taskID: taskID)
          deleted()
        }
      }
      Button("Cancel", role: .cancel) {}
    } message: {
      Text("The deletion is saved locally and synchronized when the service is available.")
    }
  }

  private var displayedTask: TaskRecord? {
    store.task(id: taskID) ?? store.conflict(taskID: taskID)?.local
      ?? store.conflict(taskID: taskID)?.remote
  }

  private func taskHeader(_ task: TaskRecord) -> some View {
    HStack(alignment: .firstTextBaseline, spacing: 12) {
      Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
        .font(.largeTitle)
        .foregroundStyle(task.isCompleted ? Color.accentColor : .secondary)

      VStack(alignment: .leading, spacing: 6) {
        Text(task.title)
          .font(.largeTitle.weight(.semibold))
          .strikethrough(task.isCompleted)
          .textSelection(.enabled)
        TaskSyncBadge(state: task.syncState)
          .font(.caption)
      }
    }
  }

  private func details(_ task: TaskRecord) -> some View {
    Grid(alignment: .leading, horizontalSpacing: 24, verticalSpacing: 12) {
      if let projectID = task.projectID,
        let project = store.displayedProject(id: projectID)
      {
        GridRow {
          Label("Project", systemImage: "folder")
            .foregroundStyle(project.color.color)
          TaskProjectLabel(project: project)
        }
      }

      if let notes = task.notes {
        GridRow {
          Label("Notes", systemImage: "note.text")
            .foregroundStyle(.secondary)
          Text(notes)
            .textSelection(.enabled)
        }
      }

      GridRow {
        Label("Priority", systemImage: task.priority.systemImage)
          .foregroundStyle(task.priority.color)
        Text(task.priority.title)
      }

      if let dueDate = task.dueDate {
        GridRow {
          Label("Due", systemImage: "calendar")
            .foregroundStyle(.secondary)
          Text(dueDate.date, format: .dateTime.year().month().day())
        }
      } else if let dueAt = task.dueAt {
        GridRow {
          Label("Due", systemImage: "calendar")
            .foregroundStyle(.secondary)
          Text(dueAt, format: .dateTime.year().month().day().hour().minute())
        }
      }

      GridRow {
        Label("Updated", systemImage: "clock")
          .foregroundStyle(.secondary)
        Text(task.updatedAt, format: .relative(presentation: .named))
      }
    }
  }

  private func save(
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
}
