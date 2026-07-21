import SwiftUI

struct ContentView: View {
    @State private var showContent = false

    var body: some View {
        VStack {
            Button("点击我！") {
                withAnimation {
                    showContent = !showContent
                }
            }

            if showContent {
                VStack(spacing: 16) {
                    Image(systemName: "swift")
                        .font(.system(size: 200))
                        .foregroundStyle(.tint)
                    Text("SwiftUI: \(GreetingBridge.greet())")
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding()
    }
}

#Preview {
    ContentView()
}
