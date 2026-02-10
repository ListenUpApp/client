import SwiftUI
import Shared
import UIKit

/// Contributor (Author/Narrator) detail screen.
///
/// Features:
/// - Hero header with circular avatar
/// - Name and aliases
/// - Biography (expandable)
/// - Books organized by role (Written By, Narrated By, etc.)
/// - Horizontal book carousels per role
/// - Progress indicators on books
struct ContributorDetailView: View {
    let contributorId: String

    @Environment(\.dependencies) private var deps
    @Environment(\.dismiss) private var dismiss
    @State private var observer: ContributorDetailObserver?

    var body: some View {
        Group {
            if let observer, !observer.isLoading {
                content(observer: observer)
            } else {
                loadingView
            }
        }
        .background(Color(.systemBackground))
        .navigationTitle(observer?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if let observer {
                    Menu {
                        Button(action: {}) {
                            Label(NSLocalizedString("common.edit", comment: ""), systemImage: "pencil")
                        }
                        Button(role: .destructive, action: {
                            observer.onDeleteContributor()
                        }) {
                            Label(NSLocalizedString("common.delete", comment: ""), systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
        .confirmationDialog(
            NSLocalizedString("contributor_detail.delete_contributor", comment: ""),
            isPresented: Binding(
                get: { observer?.showDeleteConfirmation ?? false },
                set: { _ in observer?.onDismissDelete() }
            ),
            titleVisibility: .visible
        ) {
            Button(NSLocalizedString("common.delete", comment: ""), role: .destructive) {
                observer?.onConfirmDelete {
                    dismiss()
                }
            }
            Button(NSLocalizedString("common.cancel", comment: ""), role: .cancel) {}
        } message: {
            Text(NSLocalizedString("contributor_detail.remove_from_library", comment: ""))
        }
        .onAppear {
            if observer == nil {
                let vm = deps.createContributorDetailViewModel()
                observer = ContributorDetailObserver(viewModel: vm)
                observer?.loadContributor(contributorId: contributorId)
            }
        }
        .onDisappear {
            observer?.stopObserving()
        }
    }

    // MARK: - Content

    private func content(observer: ContributorDetailObserver) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header section
                headerSection(observer: observer)

                // Biography
                if let bio = observer.bio, !bio.isEmpty {
                    bioSection(bio: bio)
                        .padding(.horizontal)
                }

                // Role sections (Written By, Narrated By, etc.)
                ForEach(observer.roleSections, id: \.role) { section in
                    roleSectionView(section: section, observer: observer)
                }
            }
            .padding(.bottom, 32)
        }
    }

    // MARK: - Header Section

    private func headerSection(observer: ContributorDetailObserver) -> some View {
        VStack(spacing: 16) {
            // Avatar
            avatarView(observer: observer)
                .frame(width: 120, height: 120)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.2), radius: 16, x: 0, y: 8)

            // Name and info
            VStack(spacing: 6) {
                Text(observer.name)
                    .font(.title2.bold())

                // Aliases
                if !observer.aliases.isEmpty {
                    Text(String(format: NSLocalizedString("contributor_detail.aka", comment: ""), observer.aliases.joined(separator: ", ")))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .italic()
                }

                // Book count
                Text("\(observer.totalBookCount) \(observer.totalBookCount == 1 ? NSLocalizedString("contributor_detail.audiobook_count", comment: "singular") : NSLocalizedString("contributor_detail.audiobooks_count", comment: "plural"))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                // Dates (if available)
                if let dateString = formatDateRange(birth: observer.birthDate, death: observer.deathDate) {
                    Text(dateString)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }

                // Website
                if let website = observer.website, let url = URL(string: website) {
                    Link(destination: url) {
                        Label("Website", systemImage: "globe")
                            .font(.caption)
                    }
                    .foregroundStyle(Color.listenUpOrange)
                }
            }
        }
        .padding(.top, 16)
    }

    private func avatarView(observer: ContributorDetailObserver) -> some View {
        ContributorAvatar(
            name: observer.name,
            imagePath: observer.imagePath,
            blurHash: observer.imageBlurHash,
            id: contributorId,
            fontSize: 40
        )
    }

    private func formatDateRange(birth: String?, death: String?) -> String? {
        guard let birth else { return nil }

        // Extract just the year from ISO date
        let birthYear = String(birth.prefix(4))

        if let death {
            let deathYear = String(death.prefix(4))
            return "\(birthYear) - \(deathYear)"
        } else {
            return String(format: NSLocalizedString("contributor_detail.born_year", comment: ""), birthYear)
        }
    }

    // MARK: - Biography Section

    private func bioSection(bio: String) -> some View {
        ExpandableText(title: NSLocalizedString("common.about", comment: ""), text: bio, lineLimit: 4, minimumLengthForToggle: 200)
    }

    // MARK: - Role Section

    private func roleSectionView(section: RoleSection, observer: ContributorDetailObserver) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                Text(section.displayName)
                    .font(.headline)

                Text("\(section.bookCount)")
                    .font(.caption.weight(.medium))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.listenUpOrange.opacity(0.2), in: Capsule())
                    .foregroundStyle(Color.listenUpOrange)

                Spacer()

                if section.showViewAll {
                    Button(NSLocalizedString("common.view_all", comment: "")) {
                        // TODO: Navigate to full list
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.listenUpOrange)
                }
            }
            .padding(.horizontal)

            // Horizontal book carousel
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(Array(section.previewBooks), id: \.idString) { book in
                        bookCard(book: book, progress: observer.bookProgress[book.idString])
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    private func bookCard(book: Book, progress: Float?) -> some View {
        NavigationLink(value: BookDestination(id: book.idString)) {
            VStack(alignment: .leading, spacing: 8) {
                // Cover with progress overlay
                ZStack(alignment: .bottom) {
                    BookCoverImage(book: book)
                        .frame(width: 100, height: 100)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                    // Progress bar overlay
                    if let progress, progress > 0 {
                        ProgressBar(progress: progress, style: .overlay)
                            .frame(height: 4)
                    }
                }
                .shadow(color: .black.opacity(0.15), radius: 8, x: 0, y: 4)

                // Title
                Text(book.title)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .frame(width: 100, alignment: .leading)
            }
        }
        .buttonStyle(.pressScaleChip)
    }

    // MARK: - Loading View

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(NSLocalizedString("common.loading", comment: ""))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ContributorDetailView(contributorId: "preview-contributor-id")
    }
}
