import SwiftUI

struct TaskEditorView: View {
  let presentation: TaskEditorPresentation
  let save: (TaskEditorDraft) -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var draft: TaskEditorDraft

  init(
    presentation: TaskEditorPresentation,
    save: @escaping (TaskEditorDraft) -> Void
  ) {
    self.presentation = presentation
    self.save = save
    _draft = State(initialValue: presentation.draft)
  }

  var body: some View {
    NavigationStack {
      Form {
        Section("Task") {
          TextField("Title", text: $draft.title)
            .textFieldStyle(.plain)

          TextEditor(text: $draft.notes)
            .frame(minHeight: 100)
            .overlay(alignment: .bottomTrailing) {
              Text("\(draft.notes.count)/4,000")
                .font(.caption2)
                .foregroundStyle(draft.notes.count > 4_000 ? .red : .secondary)
                .padding(4)
            }
        }

        Section("Details") {
          Picker("Priority", selection: $draft.priority) {
            ForEach(TaskPriorityValue.allCases) { priority in
              Label(priority.title, systemImage: priority.systemImage)
                .tag(priority)
            }
          }

          Toggle("Due date", isOn: $draft.includesDueDate.animation())

          if draft.includesDueDate {
            DatePicker(
              "Due",
              selection: $draft.dueAt,
              displayedComponents: [.date, .hourAndMinute]
            )
          }

          if case .create = presentation.mode {
            EmptyView()
          } else {
            Toggle("Completed", isOn: $draft.isCompleted)
          }
        }

        if draft.normalizedTitle.isEmpty {
          Section {
            Label("A title is required.", systemImage: "exclamationmark.circle")
              .foregroundStyle(.red)
          }
        }
      }
      .formStyle(.grouped)
      .navigationTitle(presentation.navigationTitle)
      #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
      #endif
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Cancel", role: .cancel) {
            dismiss()
          }
        }
        ToolbarItem(placement: .confirmationAction) {
          Button("Save") {
            save(draft)
            dismiss()
          }
          .disabled(!draft.isValid)
        }
      }
    }
    #if os(macOS)
      .frame(minWidth: 460, idealWidth: 520, minHeight: 480, idealHeight: 560)
    #endif
  }
}
