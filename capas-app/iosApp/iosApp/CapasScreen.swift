import SwiftUI
import Shared
import UIKit

enum CapasCategory: CaseIterable {
    case national
    case sport
    case economy
    
    var label: String {
        switch self {
        case .national: return Strings.categoryNational
        case .sport: return Strings.categorySport
        case .economy: return Strings.categoryEconomy
        }
    }
}

struct CapasScreen: View {
    @StateObject private var viewModelWrapper = CapasViewModelWrapper()
    @State private var selectedCategory: CapasCategory = .national
    @State private var showRemoved = false
    @State private var showAbout = false
    @Namespace private var animation
    
    // Drag state
    @State private var draggingCapa: Capa?
    @State private var dragOffset: CGSize = .zero
    @State private var isOverTrash = false
    @State private var draggingInitialFrame: CGRect?
    
    let columns = [
        GridItem(.adaptive(minimum: 160), spacing: 24)
    ]
    
    // Frame tracking for reordering
    @State private var itemFrames: [String: CGRect] = [:]
    
    // Local state for optimistic reordering
    @State private var localCapas: [Capa] = []

    var body: some View {
        NavigationStack {
            ZStack {
                // Premium Background
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Custom Header
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(Date().formatted(.dateTime.weekday(.wide).month().day()))
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(.secondary)
                                .textCase(.uppercase)
                            
                            Text(Strings.appName)
                                .font(.system(size: 34, weight: .bold, design: .serif))
                                .foregroundColor(.primary)
                        }
                        Spacer()
                        
                        HStack(spacing: 8) {
                            Button(action: {
                                showRemoved = true
                            }) {
                                Image(systemName: "clock.arrow.circlepath")
                                    .font(.system(size: 20, weight: .medium))
                                    .foregroundColor(.primary)
                                    .padding(10)
                                    .background(Color(uiColor: .secondarySystemBackground))
                                    .clipShape(Circle())
                            }
                            
                            Button(action: {
                                showAbout = true
                            }) {
                                Image(systemName: "info.circle")
                                    .font(.system(size: 20, weight: .medium))
                                    .foregroundColor(.primary)
                                    .padding(10)
                                    .background(Color(uiColor: .secondarySystemBackground))
                                    .clipShape(Circle())
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 16)
                    .padding(.bottom, 24)
                    .background(Color(uiColor: .systemBackground))
                    
                    // Minimalist Category Picker
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 32) {
                            ForEach(CapasCategory.allCases, id: \.self) { category in
                                Button(action: {
                                    selectedCategory = category
                                }) {
                                    VStack(spacing: 8) {
                                        Text(category.label)
                                            .font(.system(size: 16, weight: .medium, design: .rounded))
                                            .foregroundColor(selectedCategory == category ? .primary : .secondary)
                                        
                                        if selectedCategory == category {
                                            Circle()
                                                .fill(Color.primary)
                                                .frame(width: 4, height: 4)
                                                .matchedGeometryEffect(id: "indicator", in: animation)
                                        } else {
                                            Circle()
                                                .fill(Color.clear)
                                                .frame(width: 4, height: 4)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                    }
                    .background(Color(uiColor: .systemBackground))
                    .shadow(color: Color.black.opacity(0.03), radius: 10, x: 0, y: 5)
                    .zIndex(1)
                    
                    // Grid
                    if let capasResponse = viewModelWrapper.state.capas {
                        // Sync logic handled in .onChange or initial load
                        
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 24) {
                                ForEach(localCapas, id: \.id) { capa in
                                DraggableCapaGridItem(
                                    capa: capa,
                                    draggingCapa: $draggingCapa,
                                    dragOffset: $dragOffset,
                                    isOverTrash: $isOverTrash,
                                    draggingInitialFrame: $draggingInitialFrame,
                                    localCapas: $localCapas,
                                    itemFrames: $itemFrames,
                                    viewModelWrapper: viewModelWrapper,
                                    animation: animation
                                )
                                }
                            }
                            .padding(24)
                            .padding(.bottom, 100)
                        }
                        .scrollDisabled(draggingCapa != nil)
                        .onPreferenceChange(ItemFramePreferenceKey.self) { frames in
                            self.itemFrames = frames
                        }
                        .onChange(of: selectedCategory) { newCategory in
                            updateLocalCapas(from: capasResponse, category: newCategory)
                        }
                        .onChange(of: capasResponse) { newResponse in
                            updateLocalCapas(from: newResponse, category: selectedCategory)
                        }
                        .onAppear {
                            updateLocalCapas(from: capasResponse, category: selectedCategory)
                        }
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
                                .shadow(color: Color.black.opacity(0.1), radius: 20, x: 0, y: 10)
                                .overlay(
                                    Circle()
                                        .stroke(Color.red.opacity(isOverTrash ? 0.5 : 0), lineWidth: 2)
                                )
                            
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
                // Draggable Item Overlay
                ZStack {
                    if let capa = draggingCapa, let frame = draggingInitialFrame {
                        CapaItem(capa: capa)
                            .frame(width: frame.width, height: frame.height)
                            .position(x: frame.midX + dragOffset.width, y: frame.midY + dragOffset.height)
                            .scaleEffect(1.05)
                            .shadow(color: Color.black.opacity(0.3), radius: 20, x: 0, y: 15)
                            .opacity(isOverTrash ? 0.4 : 1.0)
                            .allowsHitTesting(false)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .ignoresSafeArea()
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showRemoved) {
                RemovedCapasSheet(viewModelWrapper: viewModelWrapper)
            }
            .sheet(isPresented: $showAbout) {
                AboutSheet(viewModel: viewModelWrapper)
            }
        }
    }
    
    func updateLocalCapas(from response: CapasResponse, category: CapasCategory) {
        localCapas = getCapas(for: category, from: response)
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

struct ItemFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

struct CapaItem: View {
    let capa: Capa
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottomLeading) {
                RemoteImage(url: capa.url)
                    .aspectRatio(0.75, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .background(Color(uiColor: .secondarySystemBackground))
                
                // Gradient Overlay
                LinearGradient(
                    gradient: Gradient(colors: [.black.opacity(0.7), .clear]),
                    startPoint: .bottom,
                    endPoint: .center
                )
                
                Text(capa.nome)
                    .font(.system(.caption, design: .serif))
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .padding(12)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 5)
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
        GridItem(.adaptive(minimum: 160), spacing: 24)
    ]
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                
                if viewModelWrapper.state.removed.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "trash.slash")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(Strings.msgNoRemovedCapas)
                            .font(.headline)
                            .foregroundColor(.secondary)
                    }
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 24) {
                            ForEach(viewModelWrapper.state.removed, id: \.id) { capa in
                                Button(action: {
                                    let generator = UINotificationFeedbackGenerator()
                                    generator.notificationOccurred(.success)
                                    viewModelWrapper.restoreCapa(capa)
                                }) {
                                    CapaItem(capa: capa)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                            .stroke(Color.blue, lineWidth: 0)
                                        )
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(24)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(Strings.titleRecoverCapas)
                        .font(.system(.title3, design: .serif))
                        .fontWeight(.bold)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        presentationMode.wrappedValue.dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.secondary)
                        .symbolRenderingMode(.hierarchical)
                    }
                }
            }
        }
    }
}

struct DraggableCapaGridItem: View {
    let capa: Capa
    @Binding var draggingCapa: Capa?
    @Binding var dragOffset: CGSize
    @Binding var isOverTrash: Bool
    @Binding var draggingInitialFrame: CGRect?
    @Binding var localCapas: [Capa]
    @Binding var itemFrames: [String: CGRect]
    @ObservedObject var viewModelWrapper: CapasViewModelWrapper
    var animation: Namespace.ID
    
    var body: some View {
        ZStack {
            // Placeholder when dragging
            if draggingCapa?.id == capa.id {
                ZStack {
                    CapaItem(capa: capa)
                        .opacity(0)
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(Color.gray.opacity(0.1))
                }
            } else {
                NavigationLink(destination: CapasDetailsScreen(capa: capa)) {
                    CapaItem(capa: capa)
                }
                .buttonStyle(PlainButtonStyle())
                .matchedGeometryEffect(id: capa.id, in: animation)
            }
        }
        .background(
            GeometryReader { geo in
                Color.clear.preference(key: ItemFramePreferenceKey.self, value: [capa.id: geo.frame(in: .global)])
            }
        )
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
                                draggingInitialFrame = itemFrames[capa.id]
                                withAnimation(.interactiveSpring(response: 0.3, dampingFraction: 0.7)) {
                                    draggingCapa = capa
                                }
                            }
                            dragOffset = dragValue.translation
                            
                            // Reordering Logic
                            let dragLocation = dragValue.location
                            
                            // Check if over another item
                            if let targetId = itemFrames.first(where: { key, frame in
                                key != draggingCapa?.id && frame.contains(dragLocation)
                            })?.key {
                                if let fromIndex = localCapas.firstIndex(where: { $0.id == draggingCapa?.id }),
                                   let toIndex = localCapas.firstIndex(where: { $0.id == targetId }),
                                   fromIndex != toIndex {
                                    
                                    withAnimation(.spring()) {
                                        let movedCapa = localCapas.remove(at: fromIndex)
                                        localCapas.insert(movedCapa, at: toIndex)
                                    }
                                    
                                    let generator = UISelectionFeedbackGenerator()
                                    generator.selectionChanged()
                                }
                            }
                            
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
                        // Also remove from local
                        if let index = localCapas.firstIndex(where: { $0.id == capa.id }) {
                             localCapas.remove(at: index)
                        }
                    } else {
                        // Save Order
                        if draggingCapa != nil {
                            viewModelWrapper.updateCapaOrder(localCapas)
                        }
                    }
                    
                    // Reset state
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                        draggingCapa = nil
                        dragOffset = .zero
                        isOverTrash = false
                        draggingInitialFrame = nil
                    }
                }
        )
    }
}
