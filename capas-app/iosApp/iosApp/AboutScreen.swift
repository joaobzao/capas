import SwiftUI

struct AboutSheet: View {
    @Environment(\.presentationMode) var presentationMode
    @Environment(\.openURL) var openURL
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()
                
                VStack(spacing: 24) {
                    // App Info
                    VStack(spacing: 16) {
                        Image(systemName: "info.circle.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.blue)
                        
                        Text(Strings.appName)
                            .font(.system(.title, design: .serif))
                            .fontWeight(.bold)
                        
                        Text(Strings.versionLabel("1.0.1"))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(Color(uiColor: .secondarySystemGroupedBackground))
                    .cornerRadius(20)
                    .padding(.horizontal, 24)
                    .padding(.top, 24)
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text(Strings.titleContacts)
                            .font(.headline)
                            .padding(.leading, 8)
                        
                        VStack(spacing: 16) {
                            ContactItem(
                                icon: "envelope.fill",
                                title: Strings.labelSupportEmail,
                                subtitle: "joaozao.dev@gmail.com",
                                action: {
                                    if let url = URL(string: "mailto:joaozao.dev@gmail.com") {
                                        openURL(url)
                                    }
                                }
                            )
                            
                            Divider()
                            
                            ContactItem(
                                icon: "lock.fill",
                                title: Strings.labelPrivacyPolicy,
                                subtitle: Strings.subtitlePrivacyPolicy,
                                action: {
                                    if let url = URL(string: "https://github.com/joaobzao/capas/blob/main/PRIVACY_POLICY.md") {
                                        openURL(url)
                                    }
                                }
                            )
                        }
                        .padding(16)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(20)
                    }
                    .padding(.horizontal, 24)
                    
                    Spacer()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(Strings.titleAbout)
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

struct ContactItem: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.blue)
                    .cornerRadius(12)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.primary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.tertiaryLabel)
            }
        }
    }
}

extension Color {
    static let tertiaryLabel = Color(uiColor: .tertiaryLabel)
}
