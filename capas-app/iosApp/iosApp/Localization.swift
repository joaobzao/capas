import Foundation

enum Strings {
    static var isPortuguese: Bool {
        Locale.current.identifier.starts(with: "pt")
    }
    
    static var appName: String { "Capas" }
    
    static var categoryNational: String { isPortuguese ? "Generalistas" : "General" }
    static var categorySport: String { isPortuguese ? "Desporto" : "Sports" }
    static var categoryEconomy: String { isPortuguese ? "Economia e Gestão" : "Economy & Management" }
    
    static var titleAbout: String { isPortuguese ? "Sobre" : "About" }
    static var titleRecoverCapas: String { isPortuguese ? "Recuperar Capas" : "Recover Covers" }
    static var titleContacts: String { isPortuguese ? "Contactos" : "Contacts" }
    
    static var actionViewRemoved: String { isPortuguese ? "Ver capas removidas" : "View removed covers" }
    static var actionClose: String { isPortuguese ? "Fechar" : "Close" }
    static var actionUndo: String { isPortuguese ? "Anular" : "Undo" }
    static var actionRemove: String { isPortuguese ? "Remover" : "Remove" }
    static var actionRestore: String { isPortuguese ? "Restaurar" : "Restore" }
    
    static var msgRemoved: String { isPortuguese ? "removida" : "removed" }
    static var msgNoRemovedCapas: String { isPortuguese ? "Nenhuma capa removida" : "No removed covers" }
    
    static var labelSupportEmail: String { isPortuguese ? "Email de Suporte" : "Support Email" }
    static var labelPrivacyPolicy: String { isPortuguese ? "Política de Privacidade" : "Privacy Policy" }
    static var subtitlePrivacyPolicy: String { isPortuguese ? "Ler termos e condições" : "Read terms and conditions" }
    
    static func versionLabel(_ version: String) -> String {
        isPortuguese ? "Versão \(version)" : "Version \(version)"
    }
}
