import SwiftUI
import StoreKit
import Shared

struct ContentView: View {
    @Environment(\.requestReview) private var requestReview
    @State private var didCheckReview = false

    var body: some View {
        CapasScreen()
            .onAppear(perform: maybeRequestReview)
    }

    /// On the launch that crosses the engagement threshold, ask StoreKit to show
    /// the review prompt. The shared `RatingManager` fires `true` only once ever;
    /// `didCheckReview` guards against `onAppear` running more than once per launch.
    private func maybeRequestReview() {
        guard !didCheckReview else { return }
        didCheckReview = true

        if RatingManagerHelper().ratingManager.registerAppOpenAndCheck() {
            requestReview()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
