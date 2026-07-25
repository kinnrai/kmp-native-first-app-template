import SwiftUI

struct TaskProjectEditorView: View {
  let presentation: TaskProjectEditorPresentation
  let save: (TaskProjectEditorDraft) -> Void

  @Environment(\.dismiss) private var dismiss
  @State private var draft: TaskProjectEditorDraft

  init(
    presentation: TaskProjectEditorPresentation,
    save: @escaping (TaskProjectEditorDraft) -> Void
  ) {
    self.presentation = presentation
    self.save = save
    _draft = State(initialValue: presentation.draft)
  }

  var body: some View {
    NavigationStack {
      Form {
        Section("Project") {
          TextField("Name", text: $draft.name)

          Picker("Color", selection: $draft.color) {
            ForEach(TaskProjectColorValue.allCases) { color in
              Label {
                Text(color.title)
              } icon: {
                Image(systemName: "circle.fill")
                  .foregroundStyle(color.color)
              }
              .tag(color)
            }
          }
        }

        if draft.normalizedName.isEmpty {
          Section {
            Label("A name is required.", systemImage: "exclamationmark.circle")
              .foregroundStyle(.red)
          }
        } else if draft.normalizedName.count > 80 {
          Section {
            Label(
              "Project names can contain up to 80 characters.",
              systemImage: "exclamationmark.circle"
            )
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
      .frame(minWidth: 420, idealWidth: 480, minHeight: 300, idealHeight: 360)
    #endif
  }
}
