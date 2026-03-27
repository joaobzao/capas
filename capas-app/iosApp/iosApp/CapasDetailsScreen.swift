import SwiftUI
import Shared

struct CapasDetailsScreen: View {
    let capa: Capa
    @Environment(\.presentationMode) var presentationMode

    private static func todayString() -> String {
        let formatter = Foundation.DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    private static func currentLanguage() -> String {
        let lang = Locale.current.language.languageCode?.identifier ?? "en"
        return ["pt", "en", "es"].contains(lang) ? lang : "en"
    }

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.edgesIgnoringSafeArea(.all)
                
                AsyncImage(url: URL(string: capa.url)) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .scaleEffect(scale)
                            .offset(offset)
                            .gesture(
                                MagnificationGesture()
                                    .onChanged { value in
                                        let delta = value / lastScale
                                        lastScale = value
                                        scale *= delta
                                    }
                                    .onEnded { _ in
                                        lastScale = 1.0
                                        if scale < 1.0 {
                                            withAnimation {
                                                scale = 1.0
                                                offset = .zero
                                            }
                                        }
                                    }
                            )
                            .simultaneousGesture(
                                DragGesture()
                                    .onChanged { value in
                                        let newOffset = CGSize(
                                            width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height
                                        )
                                        offset = newOffset
                                    }
                                    .onEnded { _ in
                                        lastOffset = offset
                                    }
                            )
                    case .failure:
                        Image(systemName: "photo")
                            .foregroundColor(.white)
                    @unknown default:
                        EmptyView()
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 2) {
                    Text(capa.nome)
                        .foregroundColor(.white)
                        .font(.headline)
                    if let dateText = RelativeDateFormatter.formatRelativeDate(
                        dateString: capa.lastUpdated,
                        todayString: Self.todayString(),
                        language: Self.currentLanguage(),
                        includeYear: true
                    ) {
                        Text(dateText)
                            .foregroundColor(.white.opacity(0.7))
                            .font(.caption)
                    }
                }
            }
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: {
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                }
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}
