import Foundation

enum Strings {
    static var appName: String { NSLocalizedString("app_name", comment: "App name") }
    
    static var categoryNational: String { NSLocalizedString("category_national", comment: "") }
    static var categorySport: String { NSLocalizedString("category_sport", comment: "") }
    static var categoryEconomy: String { NSLocalizedString("category_economy", comment: "") }
    static var categoryRegional: String { NSLocalizedString("category_regional", comment: "") }
    
    static var titleAbout: String { NSLocalizedString("title_about", comment: "") }
    static var titleRecoverCapas: String { NSLocalizedString("title_recover_capas", comment: "") }
    static var titleContacts: String { NSLocalizedString("title_contacts", comment: "") }
    static var titleCapasUpdate: String { NSLocalizedString("title_capas_update", comment: "") }
    
    static var actionViewRemoved: String { NSLocalizedString("action_view_removed", comment: "") }
    static var actionClose: String { NSLocalizedString("action_close", comment: "") }
    static var actionUndo: String { NSLocalizedString("action_undo", comment: "") }
    static var actionRemove: String { NSLocalizedString("action_remove", comment: "") }
    static var actionRestore: String { NSLocalizedString("action_restore", comment: "") }
    
    static var msgRemoved: String { NSLocalizedString("msg_removed", comment: "") }
    static var msgNoRemovedCapas: String { NSLocalizedString("msg_no_removed_capas", comment: "") }
    static var msgUpdateFailed: String { NSLocalizedString("msg_update_failed", comment: "") }
    
    static var labelSupportEmail: String { NSLocalizedString("label_support_email", comment: "") }
    static var labelPrivacyPolicy: String { NSLocalizedString("label_privacy_policy", comment: "") }
    static var subtitlePrivacyPolicy: String { NSLocalizedString("subtitle_privacy_policy", comment: "") }
    
    static func versionLabel(_ version: String) -> String {
        String(format: NSLocalizedString("version_label", comment: ""), version)
    }
    
    static var titleSupport: String { NSLocalizedString("title_support", comment: "") }
    static var labelBuyCoffee: String { NSLocalizedString("label_buy_coffee", comment: "") }
    static var subtitleBuyCoffee: String { NSLocalizedString("subtitle_buy_coffee", comment: "") }

    static func msgUpdatedAt(_ date: String) -> String {
        String(format: NSLocalizedString("msg_updated_at", comment: ""), date)
    }
}
