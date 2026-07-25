import Foundation

enum AppConfiguration {
  nonisolated static let taskAPIBaseURL: URL = {
    guard
      let value = Bundle.main.object(forInfoDictionaryKey: "TaskAPIBaseURL") as? String,
      let url = URL(string: value)
    else {
      preconditionFailure("TaskAPIBaseURL must be a valid URL in the app's Info.plist.")
    }
    return url
  }()
}
