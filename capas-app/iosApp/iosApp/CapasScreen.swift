import SwiftUI
import Shared

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
                VStack {
                    // Category Picker
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(CapasCategory.allCases, id: \.self) { category in
                                Button(action: {
                                    selectedCategory = category
                                }) {
                                    Text(category.rawValue)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 8)
                                        .background(selectedCategory == category ? Color.blue.opacity(0.2) : Color.gray.opacity(0.1))
                                        .foregroundColor(selectedCategory == category ? .blue : .primary)
                                        .cornerRadius(16)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(selectedCategory == category ? Color.blue : Color.clear, lineWidth: 1)
                                        )
                                }
                            }
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                    }
                    
                    // Grid
                    if let capasResponse = viewModelWrapper.state.capas {
                        let capas = getCapas(for: selectedCategory, from: capasResponse)
                        
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 16) {
                                ForEach(capas, id: \.id) { capa in
                                    ZStack {
                                        // Placeholder when dragging
                                        if draggingCapa?.id == capa.id {
                                            Rectangle()
                                                .fill(Color.gray.opacity(0.3))
                                                .cornerRadius(12)
                                                .aspectRatio(0.75, contentMode: .fit)
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
                                        LongPressGesture(minimumDuration: 0.3)
                                            .sequenced(before: DragGesture(coordinateSpace: .global))
                                            .onChanged { value in
                                                switch value {
                                                case .second(true, let drag):
                                                    if let dragValue = drag {
                                                        if draggingCapa == nil {
                                                            let generator = UIImpactFeedbackGenerator(style: .medium)
                                                            generator.impactOccurred()
                                                            draggingCapa = capa
                                                        }
                                                        dragOffset = dragValue.translation
                                                        
                                                        // Check if over trash (approximate screen height check)
                                                        let screenHeight = UIScreen.main.bounds.height
                                                        let trashThreshold = screenHeight - 150 // Bottom area
                                                        isOverTrash = dragValue.location.y > trashThreshold
                                                    }
                                                default:
                                                    break
                                                }
                                            }
                                            .onEnded { value in
                                                if isOverTrash, let capa = draggingCapa {
                                                    viewModelWrapper.removeCapa(capa)
                                                }
                                                
                                                // Reset state
                                                withAnimation {
                                                    draggingCapa = nil
                                                    dragOffset = .zero
                                                    isOverTrash = false
                                                }
                                            }
                                    )
                                }
                            }
                            .padding()
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
                            Rectangle()
                                .fill(isOverTrash ? Color.red.opacity(0.2) : Color.gray.opacity(0.2))
                                .frame(height: 100)
                            
                            Image(systemName: "trash")
                                .font(.system(size: 30))
                                .foregroundColor(isOverTrash ? .red : .gray)
                                .scaleEffect(isOverTrash ? 1.2 : 1.0)
                                .animation(.spring(), value: isOverTrash)
                        }
                    }
                    .edgesIgnoringSafeArea(.bottom)
                    .transition(.move(edge: .bottom))
                }
                
                // Draggable Item Overlay
                if let capa = draggingCapa {
                    ZStack {
                        CapaItem(capa: capa)
                            .offset(dragOffset)
                            .opacity(0.9)
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
                        Image(systemName: "trash")
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
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: URL(string: capa.url)) { phase in
                switch phase {
                case .empty:
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .aspectRatio(0.75, contentMode: .fit)
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(0.75, contentMode: .fit)
                        .cornerRadius(8)
                case .failure:
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .aspectRatio(0.75, contentMode: .fit)
                        .overlay(Image(systemName: "photo"))
                @unknown default:
                    EmptyView()
                }
            }
            
            Text(capa.nome)
                .font(.subheadline)
                .lineLimit(2)
        }
        .padding(8)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 2)
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
