import SharedLogic

enum GreetingBridge {
    static func greet() -> String {
        Greeting().greet()
    }
}
