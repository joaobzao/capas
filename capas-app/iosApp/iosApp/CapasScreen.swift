//
//  CapasScreen.swift
//  iosApp
//
//  Created by João Zão on 30/09/2025.
//

import SwiftUI
import Shared // your KMP module

enum CapasCategory: String, CaseIterable, Identifiable {
    case national = "Jornais Nacionais"
    case sport = "Desporto"
    case economy = "Economia e Gestão"

    var id: String { rawValue }
}

//class IOSCapasViewModel: ObservableObject {
//    private let delegate: CapasViewModel
//    private var handle: Kotlinx_coroutines_coreJob?
//
//    @Published var state: CapasViewState = CapasViewState(capas: nil, removed: [])
//
//    init() {
//        delegate = CapasViewModel(log: KermitLogger(), capasRepository: CapasRepository())
//        observe()
//    }
//
//    func observe() {
//        handle = delegate.capasState.watch { [weak self] newState in
//            if let s = newState as? CapasViewState {
//                DispatchQueue.main.async {
//                    self?.state = s
//                }
//            }
//        }
//        delegate.getCapas()
//    }
//
//    func remove(_ capa: Capa) {
//        delegate.removeCapa(capa: capa)
//    }
//
//    func restore(_ capa: Capa) {
//        delegate.restoreCapa(capa: capa)
//    }
//}


struct CapasScreen: View {
    //@StateObject var viewModel = IOSCapasViewModel()
    @State private var selectedCategory: CapasCategory = .national
    @State private var showRemoved = false
    @State private var draggingCapa: Capa? = nil
    @State private var dragOffset: CGSize = .zero
    @State private var isOverTrash = false

    let columns = [GridItem(.adaptive(minimum: 160), spacing: 16)]
    
    var mockedCapasResponse: CapasResponse? = CapasResponse(
        mainNewspapers: [
            Capa(id: "1", nome: "Público", url: "https://example.com/capas/publico.jpg"),
            Capa(id: "2", nome: "Diário de Notícias", url: "https://example.com/capas/dn.jpg"),
            Capa(id: "3", nome: "Correio da Manhã", url: "https://example.com/capas/cm.jpg")
        ],
        sportNewspapers: [
            Capa(id: "4", nome: "A Bola", url: "https://example.com/capas/abola.jpg"),
            Capa(id: "5", nome: "Record", url: "https://example.com/capas/record.jpg"),
            Capa(id: "6", nome: "O Jogo", url: "https://example.com/capas/ojogo.jpg")
        ],
        economyNewspapers: [
            Capa(id: "7", nome: "Jornal de Negócios", url: "https://example.com/capas/negocios.jpg"),
            Capa(id: "8", nome: "Expresso Economia", url: "https://example.com/capas/expresso.jpg"),
            Capa(id: "9", nome: "Financial Times (PT)", url: "https://example.com/capas/ft.jpg")
        ]
    )


    var body: some View {
        NavigationStack {
            VStack {
                // Category chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(CapasCategory.allCases) { category in
                            Button(action: { selectedCategory = category }) {
                                Text(category.rawValue)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(category == selectedCategory ? Color.accentColor.opacity(0.2) : Color.gray.opacity(0.2))
                                    .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal)
                }

                // Grid
                if let capasResponse = mockedCapasResponse {
                    let items: [Capa] = {
                        switch selectedCategory {
                        case .national: return capasResponse.mainNewspapers
                        case .sport: return capasResponse.sportNewspapers
                        case .economy: return capasResponse.economyNewspapers
                        }
                    }()

                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 16) {
                            ForEach(items, id: \.id) { capa in
                                CapaGridItem(capa: capa)
                                    .gesture(
                                        DragGesture()
                                            .onChanged { value in
                                                draggingCapa = capa
                                                dragOffset = value.translation
                                            }
                                            .onEnded { value in
                                                if isOverTrash {
                                                    //viewModel.remove(capa)
                                                }
                                                draggingCapa = nil
                                                dragOffset = .zero
                                                isOverTrash = false
                                            }
                                    )
                            }
                        }
                        .padding()
                    }
                } else {
                    ProgressView()
                        .frame(maxHeight: .infinity)
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showRemoved = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
            .navigationTitle("Capas")
            .sheet(isPresented: $showRemoved) {
                //RemovedCapasSheet(viewModel: viewModel)
                //RemovedCapasSheet()
            }
            .overlay(alignment: .bottom) {
                if draggingCapa != nil {
                    TrashZone(isOverTrash: $isOverTrash)
                }
            }
        }
    }
}

struct CapaGridItem: View {
    let capa: Capa

    var body: some View {
        VStack {
            AsyncImage(url: URL(string: capa.url)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.gray.opacity(0.3)
            }
            .frame(height: 200)
            .clipped()
            Text(capa.nome)
                .font(.body)
                .lineLimit(2)
        }
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 2)
    }
}

struct TrashZone: View {
    @Binding var isOverTrash: Bool

    var body: some View {
        Rectangle()
            .fill(isOverTrash ? Color.red.opacity(0.5) : Color.gray.opacity(0.2))
            .frame(height: 80)
            .overlay {
                Image(systemName: "trash.fill")
                    .font(.title)
                    .foregroundColor(isOverTrash ? .red : .gray)
            }
            .onDrop(of: [.text], isTargeted: $isOverTrash) { _ in true }
    }
}

//struct RemovedCapasSheet: View {
//    //@ObservedObject var viewModel: IOSCapasViewModel
//
//    var body: some View {
//        VStack {
//            Text("Capas removidas")
//                .font(.title3)
//                .padding()
//
//            if viewModel.state.removed.isEmpty {
//                Text("Nenhuma capa removida")
//            } else {
//                ScrollView {
//                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120))]) {
//                        ForEach(viewModel.state.removed, id: \.id) { capa in
//                            VStack {
//                                AsyncImage(url: URL(string: capa.url)) { image in
//                                    image.resizable().scaledToFill()
//                                } placeholder: {
//                                    Color.gray.opacity(0.3)
//                                }
//                                .frame(height: 160)
//                                .clipped()
//                                Text(capa.nome).font(.footnote)
//                            }
//                            .onTapGesture { viewModel.restore(capa) }
//                        }
//                    }
//                    .padding()
//                }
//            }
//        }
//    }
//}
