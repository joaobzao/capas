import SwiftUI
import Shared
import UIKit

enum CapasCategory: String, CaseIterable {
    case national = "Jornais Nacionais"
    case sport = "Desporto"
    case economy = "Economia e Gestão"
}

struct CapasScreen: View {
    @StateObject private var viewModelWrapper = CapasViewModelWrapper()
    @State private var selectedCategory: CapasCategory = .national
    @State private var showRemoved = false
    @Namespace private var animation
    
    // Drag state
    @State private var draggingCapa: Capa?
    @State private var dragOffset: CGSize = .zero
    @State private var isOverTrash = false
    
    let columns = [
        GridItem(.adaptive(minimum: 160), spacing: 16)
    ]
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Category Picker
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(CapasCategory.allCases, id: \.self) { category in
                                Button(action: {
                                    selectedCategory = category
                                }) {
                                    Text(category.rawValue)
                                        .font(.system(.subheadline, design: .rounded))
                                        .fontWeight(.semibold)
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 10)
                                        .background(
                                            Capsule()
                                                .fill(selectedCategory == category ? Color.blue : Color(uiColor: .secondarySystemFill))
                                        )
                                        .foregroundColor(selectedCategory == category ? .white : .primary)
                                }
                            }
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 16)
                    }
                    .background(Color(uiColor: .systemBackground))
                    
                    // Grid
                    if let capasResponse = viewModelWrapper.state.capas {
                        let capas = getCapas(for: selectedCategory, from: capasResponse)
                        
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 20) {
                                ForEach(capas, id: \.id) { capa in
                                    ZStack {
                                        // Placeholder when dragging
                                        if draggingCapa?.id == capa.id {
                                            ZStack {
                                                CapaItem(capa: capa)
                                                    .opacity(0)
                                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                    .fill(Color.gray.opacity(0.1))
                                            }
                                            .matchedGeometryEffect(id: capa.id, in: animation)
                                        } else {
                                            NavigationLink(destination: CapasDetailsScreen(capa: capa)) {
                                                CapaItem(capa: capa)
                                            }
                                            .buttonStyle(PlainButtonStyle())
                                            .matchedGeometryEffect(id: capa.id, in: animation)
                                        }
                                    }
                                    .simultaneousGesture(
                                        LongPressGesture(minimumDuration: 0.15)
                                            .sequenced(before: DragGesture(coordinateSpace: .global))
                                            .onChanged { value in
                                                switch value {
                                                case .second(true, let drag):
                                                    if let dragValue = drag {
                                                        if draggingCapa == nil {
                                                            let generator = UIImpactFeedbackGenerator(style: .medium)
                                                            generator.impactOccurred()
                                                            withAnimation(.interactiveSpring()) {
                                                                draggingCapa = capa
                                                            }
                                                        }
                                                        dragOffset = dragValue.translation
                                                        
                                                        // Check if over trash
                                                        let screenHeight = UIScreen.main.bounds.height
                                                        let trashThreshold = screenHeight - 150
                                                        withAnimation(.spring()) {
                                                            isOverTrash = dragValue.location.y > trashThreshold
                                                        }
                                                    }
                                                default:
                                                    break
                                                }
                                            }
                                            .onEnded { value in
                                                if isOverTrash, let capa = draggingCapa {
                                                    let generator = UINotificationFeedbackGenerator()
                                                    generator.notificationOccurred(.success)
                                                    viewModelWrapper.removeCapa(capa)
                                                }
                                                
                                                // Reset state
                                                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                                                    draggingCapa = nil
                                                    dragOffset = .zero
                                                    isOverTrash = false
                                                }
                                            }
                                    )
                                }
                            }
                            .padding()
                            .padding(.bottom, 100) // Space for trash
                        }
                        .scrollDisabled(draggingCapa != nil)
                    } else {
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                
                // Trash Drop Zone
                if draggingCapa != nil {
                    VStack {
                        Spacer()
                        ZStack {
                            Circle()
                                .fill(.ultraThinMaterial)
                                .frame(width: 80, height: 80)
                                .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 5)
                            
                            Image(systemName: isOverTrash ? "trash.fill" : "trash")
                                .font(.system(size: 32))
                                .foregroundColor(isOverTrash ? .red : .secondary)
                                .scaleEffect(isOverTrash ? 1.2 : 1.0)
                        }
                        .padding(.bottom, 30)
                    }
                    .edgesIgnoringSafeArea(.bottom)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                
                // Draggable Item Overlay
                if let capa = draggingCapa {
                    ZStack {
                        CapaItem(capa: capa)
                            .offset(dragOffset)
                            .scaleEffect(1.05)
                            .shadow(color: Color.black.opacity(0.2), radius: 15, x: 0, y: 10)
                            .opacity(isOverTrash ? 0.4 : 1.0)
                    }
                    .matchedGeometryEffect(id: capa.id, in: animation, properties: .frame, isSource: false)
                    .allowsHitTesting(false)
                }
            }
            .navigationTitle("Capas")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showRemoved = true
                    }) {
                        Image(systemName: "arrow.counterclockwise.circle")
                            .font(.system(size: 18, weight: .semibold))
                    }
                }
            }
            .sheet(isPresented: $showRemoved) {
                RemovedCapasSheet(viewModelWrapper: viewModelWrapper)
            }
        }
    }
    
    func getCapas(for category: CapasCategory, from response: CapasResponse) -> [Capa] {
        switch category {
        case .national:
            return response.mainNewspapers
        case .sport:
            return response.sportNewspapers
        case .economy:
            return response.economyNewspapers
        }
    }
}

struct CapaItem: View {
    let capa: Capa
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RemoteImage(url: capa.url)
                .aspectRatio(0.75, contentMode: .fit)
                .frame(maxWidth: .infinity)
                .clipped()
            
            Text(capa.nome)
                .font(.system(.caption, design: .rounded))
                .fontWeight(.semibold)
                .foregroundColor(.primary)
                .lineLimit(2)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(uiColor: .secondarySystemGroupedBackground))
        }
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
    }
}

class ImageLoader: ObservableObject {
    @Published var image: Image?
    private static var cache = NSCache<NSString, UIImage>()
    
    init(url: String) {
        if let cachedImage = Self.cache.object(forKey: url as NSString) {
            self.image = Image(uiImage: cachedImage)
        }
    }
    
    func load(from urlString: String) {
        if image != nil { return }
        
        guard let url = URL(string: urlString) else { return }
        Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let uiImage = UIImage(data: data) {
                    Self.cache.setObject(uiImage, forKey: urlString as NSString)
                    DispatchQueue.main.async {
                        self.image = Image(uiImage: uiImage)
                    }
                }
            } catch {
                print("Error loading image: \(error)")
            }
        }
    }
}

struct RemoteImage: View {
    let url: String
    @StateObject private var loader: ImageLoader
    
    init(url: String) {
        self.url = url
        _loader = StateObject(wrappedValue: ImageLoader(url: url))
    }
    
    var body: some View {
        Group {
            if let image = loader.image {
                image
                    .resizable()
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .overlay(ProgressView())
                    .onAppear {
                        loader.load(from: url)
                    }
            }
        }
    }
}

struct RemovedCapasSheet: View {
    @ObservedObject var viewModelWrapper: CapasViewModelWrapper
    @Environment(\.presentationMode) var presentationMode
    
    let columns = [
        GridItem(.adaptive(minimum: 120), spacing: 12)
    ]
    
    var body: some View {
        NavigationView {
            VStack {
                if viewModelWrapper.state.removed.isEmpty {
                    Text("Nenhuma capa removida")
                        .foregroundColor(.gray)
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 12) {
                            ForEach(viewModelWrapper.state.removed, id: \.id) { capa in
                                Button(action: {
                                    viewModelWrapper.restoreCapa(capa)
                                }) {
                                    CapaItem(capa: capa)
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("Capas removidas")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fechar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
}
