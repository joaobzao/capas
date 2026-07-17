import SwiftUI
import StoreKit

import Shared

/// Numeric App Store ID for the deep link to the review page. The app isn't
/// published yet, so this is a placeholder and the manual button falls back to
/// the StoreKit review prompt. Once live, set this and enable the URL path in
/// `rateApp()`.
private let APP_STORE_ID = "TODO_APP_STORE_ID"

struct AboutSheet: View {
    @ObservedObject var viewModel: CapasViewModelWrapper
    @Environment(\.presentationMode) var presentationMode
    @Environment(\.openURL) var openURL
    @Environment(\.requestReview) var requestReview
    
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
                    
                    if let run = viewModel.state.workflowStatus {
                        let isSuccess = run.conclusion == "success"
                        let statusColor = isSuccess ? Color.green : Color.red
                        let icon = isSuccess ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
                        
                        VStack(spacing: 16) {
                            ContactItem(
                                icon: icon,
                                title: Strings.titleCapasUpdate,
                                subtitle: isSuccess ? Strings.msgUpdatedAt(formatDate(run.updatedAt)) : Strings.msgUpdateFailed,
                                iconColor: statusColor,
                                action: nil
                            )
                        }
                        .padding(16)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(20)
                        .padding(.bottom, 8)
                        .padding(.horizontal, 24)
                    }
                    
                    // Support
                    VStack(alignment: .leading, spacing: 8) {
                        Text(Strings.titleSupport)
                            .font(.headline)
                            .padding(.leading, 8)

                        VStack(spacing: 16) {
                            ContactItem(
                                icon: "heart.fill",
                                title: Strings.labelBuyCoffee,
                                subtitle: Strings.subtitleBuyCoffee,
                                iconColor: Color(red: 0.91, green: 0.12, blue: 0.39),
                                action: {
                                    if let url = URL(string: "https://revolut.me/joaoqprk") {
                                        openURL(url)
                                    }
                                }
                            )

                            Divider()

                            ContactItem(
                                icon: "star.fill",
                                title: Strings.labelRateApp,
                                subtitle: Strings.subtitleRateApp,
                                iconColor: Color(red: 1.0, green: 0.76, blue: 0.03),
                                action: { rateApp() }
                            )
                        }
                        .padding(16)
                        .background(Color(uiColor: .secondarySystemGroupedBackground))
                        .cornerRadius(20)
                    }
                    .padding(.horizontal, 24)

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
            .onAppear {
                viewModel.getWorkflowStatus()
            }
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

    /// Manual "Rate the app" action. Once published, open the App Store review
    /// page directly (Apple's recommendation for a user-tapped rate button):
    ///
    ///     if let url = URL(string: "https://apps.apple.com/app/id\(APP_STORE_ID)?action=write-review") {
    ///         openURL(url)
    ///         return
    ///     }
    ///
    /// Until then, fall back to the StoreKit review prompt.
    private func rateApp() {
        requestReview()
    }
}

struct ContactItem: View {
    let icon: String
    let title: String
    let subtitle: String
    var iconColor: Color = .blue
    var action: (() -> Void)? = nil
    
    var body: some View {
        Button(action: { action?() }) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(iconColor)
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
                
                if action != nil {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.tertiaryLabel)
                }
            }
        }
        .disabled(action == nil)
    }
}

extension Color {
    static let tertiaryLabel = Color(uiColor: .tertiaryLabel)
}

private func formatDate(_ dateString: String) -> String {
    let isoFormatter = ISO8601DateFormatter()
    if let date = isoFormatter.date(from: dateString) {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM HH:mm"
        return formatter.string(from: date)
    }
    return dateString
}
