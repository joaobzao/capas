import SwiftUI
import Shared
import UIKit
import UniformTypeIdentifiers

enum CapasCategory: CaseIterable {
    case national
    case sport
    case economy
    case regional
    
    var label: String {
        switch self {
        case .national: return Strings.categoryNational
        case .sport: return Strings.categorySport
        case .economy: return Strings.categoryEconomy
        case .regional: return Strings.categoryRegional
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
    @State private var isDragging = false
    @State private var isOverTrash = false
    @State private var activeDropTargetCount = 0
    @State private var draggedCapaId: String? = nil
    
    let columns = [
        GridItem(.adaptive(minimum: 130), spacing: 24)
    ]
    
    // Local state for optimistic reordering
    @State private var localCapas: [Capa] = []

    var body: some View {
        NavigationStack {
            ZStack {
                // Premium Background
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                    .onDrop(of: [.text], delegate: DragEndDetector(isDragging: $isDragging))
                
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
                                    isDragging: $isDragging,
                                    activeDropTargetCount: $activeDropTargetCount,
                                    draggedCapaId: $draggedCapaId,
                                    isOverTrash: $isOverTrash,
                                    localCapas: $localCapas,
                                    viewModelWrapper: viewModelWrapper,
                                    animation: animation
                                )
                                }
                            }
                            .padding(24)
                            .padding(.bottom, 100)
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
                
                // Trash Drop Zone — visible only while dragging (uses opacity to stay in view tree)
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
                    .dropDestination(for: String.self) { droppedIds, location in
                        guard let droppedId = droppedIds.first,
                              let capaToRemove = localCapas.first(where: { $0.id == droppedId }) else {
                            return false
                        }
                        
                        viewModelWrapper.removeCapa(capaToRemove)
                        if let index = localCapas.firstIndex(where: { $0.id == droppedId }) {
                            withAnimation(.spring()) {
                                localCapas.remove(at: index)
                            }
                        }
                        
                        isDragging = false
                        activeDropTargetCount = 0
                        draggedCapaId = nil
                        
                        let generator = UINotificationFeedbackGenerator()
                        generator.notificationOccurred(.success)
                        
                        return true
                    } isTargeted: { targeted in
                        withAnimation(.spring()) {
                            isOverTrash = targeted
                        }
                        activeDropTargetCount += targeted ? 1 : -1
                        if activeDropTargetCount > 0 {
                            isDragging = true
                        }
                    }
                }
                .edgesIgnoringSafeArea(.bottom)
                .opacity(isDragging ? 1 : 0)
                .offset(y: isDragging ? 0 : 100)
                .animation(.spring(), value: isDragging)

                
                // Native Draggable Overlay is handled automatically by SwiftUI
            }
            .onChange(of: isDragging) { newValue in
                if !newValue {
                    draggedCapaId = nil
                }
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
        case .regional:
            return response.regionalNewspapers
        }
    }
}



struct CapaItem: View {
    let capa: Capa

    private static func todayString() -> String {
        let formatter = Foundation.DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    private static func currentLanguage() -> String {
        let lang = Locale.current.language.languageCode?.identifier ?? "en"
        return ["pt", "en", "es"].contains(lang) ? lang : "en"
    }

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
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(capa.nome)
                        .font(.system(.caption, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .lineLimit(2)

                    if let dateText = RelativeDateFormatter.formatRelativeDate(
                        dateString: capa.lastUpdated,
                        todayString: Self.todayString(),
                        language: Self.currentLanguage()
                    ) {
                        Text(dateText)
                            .font(.system(.caption2))
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(1)
                    }
                }
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
    @Binding var isDragging: Bool
    @Binding var activeDropTargetCount: Int
    @Binding var draggedCapaId: String?
    @Binding var isOverTrash: Bool
    @Binding var localCapas: [Capa]
    @ObservedObject var viewModelWrapper: CapasViewModelWrapper
    var animation: Namespace.ID
    
    var body: some View {
        NavigationLink(destination: CapasDetailsScreen(capa: capa)) {
            CapaItem(capa: capa)
        }
        .buttonStyle(PlainButtonStyle())
        .matchedGeometryEffect(id: capa.id, in: animation)
        .opacity(draggedCapaId == capa.id && isDragging ? 0.5 : 1.0)
        .onDrag {
            // Don't set isDragging here — it fires spuriously on view recreation.
            // isDragging is set via isTargeted on drop destinations instead.
            draggedCapaId = capa.id
            return NSItemProvider(object: capa.id as NSString)
        }
        .dropDestination(for: String.self) { droppedIds, location in
            // Drop completed — save the final order (swaps already happened live)
            isDragging = false
            activeDropTargetCount = 0
            draggedCapaId = nil
            viewModelWrapper.updateCapaOrder(localCapas)
            
            let generator = UISelectionFeedbackGenerator()
            generator.selectionChanged()
            
            return true
        } isTargeted: { targeted in
            activeDropTargetCount += targeted ? 1 : -1
            if activeDropTargetCount > 0 {
                isDragging = true
            }
            
            // Live reorder: swap items as the drag moves over them
            if targeted,
               let draggedId = draggedCapaId,
               draggedId != capa.id,
               let fromIndex = localCapas.firstIndex(where: { $0.id == draggedId }),
               let toIndex = localCapas.firstIndex(where: { $0.id == capa.id }) {
                withAnimation(.spring()) {
                    localCapas.move(fromOffsets: IndexSet(integer: fromIndex), toOffset: toIndex > fromIndex ? toIndex + 1 : toIndex)
                }
                let generator = UISelectionFeedbackGenerator()
                generator.selectionChanged()
            }
        }
        
    }
}

/// Catches any drag that ends without landing on a specific drop target (e.g. cancelled drags)
struct DragEndDetector: DropDelegate {
    @Binding var isDragging: Bool
    
    func performDrop(info: DropInfo) -> Bool {
        withAnimation(.spring()) {
            isDragging = false
        }
        return false
    }
    
    func dropUpdated(info: DropInfo) -> DropProposal? {
        // Return .cancel so this background zone doesn't interfere with child drop targets
        return DropProposal(operation: .cancel)
    }
    
    func dropExited(info: DropInfo) {
        withAnimation(.spring()) {
            isDragging = false
        }
    }
}
