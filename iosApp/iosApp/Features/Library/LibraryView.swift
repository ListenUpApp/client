import SwiftUI
import Shared
import UIKit

/// Library screen displaying the user's audiobook collection organized by letter.
///
/// Features:
/// - Adaptive grid: 2 columns on iPhone, 3-4 on iPad
/// - Section headers (A, B, C...) with alphabet scrubber on scroll
/// - Pull-to-refresh for manual sync
/// - Loading, empty, and error states
struct LibraryView: View {
    @Environment(CurrentUserObserver.self) private var userObserver
    @Environment(\.dependencies) private var deps

    @State private var observer: LibraryObserver?
    @State private var isScrolling = false
    @State private var scrollTarget: String?

    private var user: User? { userObserver.user }

    // Adaptive grid: 2 columns on small phones, 3+ on larger devices
    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 16)]

    var body: some View {
        Group {
            if let observer {
                libraryContent(observer: observer)
            } else {
                loadingGrid
            }
        }
        .background(Color(.systemBackground))
        .navigationTitle("Library")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: UserProfileDestination()) {
                    UserAvatarView(user: user, size: 32)
                }
                .buttonStyle(.plain)
            }
        }
        .onAppear {
            if observer == nil {
                observer = LibraryObserver(viewModel: deps.libraryViewModel)
            }
            observer?.onScreenVisible()
        }
    }

    // MARK: - Content States

    @ViewBuilder
    private func libraryContent(observer: LibraryObserver) -> some View {
        if observer.isLoading {
            loadingGrid
        } else if let errorMessage = observer.errorMessage {
            errorState(message: errorMessage, observer: observer)
        } else if observer.isEmpty {
            emptyState
        } else {
            booksGrid(observer: observer)
        }
    }

    // MARK: - Books Grid with Section Index

    private func booksGrid(observer: LibraryObserver) -> some View {
        let sections = buildSections(books: observer.books)
        let letters = sections.map { String($0.letter) }

        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    ForEach(sections, id: \.letter) { section in
                        let sectionId = "section-\(section.letter)"

                        VStack(alignment: .leading, spacing: 16) {
                            // Section header
                            Text(String(section.letter))
                                .font(.title2.bold())
                                .foregroundStyle(.primary)
                                .frame(maxWidth: .infinity, alignment: .leading)

                            // Books grid
                            LazyVGrid(columns: columns, spacing: 16) {
                                ForEach(section.books, id: \.idString) { book in
                                    let progress = observer.bookProgress[book.idString]
                                    BookCoverCard(book: book, progress: progress)
                                }
                            }
                        }
                        .id(sectionId)
                    }
                }
                .padding(.horizontal)
            }
            .refreshable {
                observer.refresh()
                try? await Task.sleep(for: .seconds(1))
            }
            .onScrollPhaseChange { _, newPhase in
                withAnimation(.easeOut(duration: 0.2)) {
                    isScrolling = newPhase != .idle
                }
            }
            // React to scroll target changes
            .onChange(of: scrollTarget) { _, newTarget in
                if let target = newTarget {
                    withAnimation(.easeOut(duration: 0.25)) {
                        proxy.scrollTo(target, anchor: .top)
                    }
                    // Clear after scrolling
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        scrollTarget = nil
                    }
                }
            }
            // Overlay the section index (no gutter)
            .overlay(alignment: .trailing) {
                SectionIndexBar(
                    letters: letters,
                    onLetterSelected: { letter in
                        scrollTarget = "section-\(letter)"
                    },
                    isVisible: isScrolling
                )
                .padding(.trailing, 8)
                .padding(.vertical, 60)
            }
        }
    }

    /// Groups books into alphabetically sorted sections.
    private func buildSections(books: [Book]) -> [(letter: Character, books: [Book])] {
        let grouped = Dictionary(grouping: books) { book -> Character in
            guard let first = book.title.first?.uppercased().first else { return "#" }
            return first.isLetter ? first : "#"
        }

        return grouped.keys
            .sorted { lhs, rhs in
                if !lhs.isLetter && rhs.isLetter { return true }
                if lhs.isLetter && !rhs.isLetter { return false }
                return lhs < rhs
            }
            .map { (letter: $0, books: grouped[$0] ?? []) }
    }

    // MARK: - Loading State

    private var loadingGrid: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 20) {
                ForEach(0 ..< 8, id: \.self) { _ in
                    BookCoverShimmer()
                }
            }
            .padding()
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "books.vertical")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)

            Text("Your Library is Empty")
                .font(.title2.bold())

            Text("Books you add to your server will appear here")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Error State

    private func errorState(message: String, observer: LibraryObserver) -> some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(.orange)

            Text("Sync Failed")
                .font(.title2.bold())

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button {
                observer.refresh()
            } label: {
                Label("Try Again", systemImage: "arrow.clockwise")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.listenUpOrange, in: Capsule())
            }
            .padding(.top, 8)

            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Section Index Bar

/// Compact alphabet index for quick section navigation.
/// Appears on the trailing edge while scrolling (like Android pattern).
private struct SectionIndexBar: View {
    let letters: [String]
    let onLetterSelected: (String) -> Void
    let isVisible: Bool

    @State private var selectedLetter: String?
    @State private var isDragging = false

    private let feedbackGenerator = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let availableHeight = geo.size.height - 16
            let letterHeight = availableHeight / CGFloat(max(letters.count, 1))

            HStack(spacing: 8) {
                // Large letter popup when dragging
                if isDragging, let letter = selectedLetter {
                    Text(letter)
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Color.listenUpOrange, in: RoundedRectangle(cornerRadius: 10))
                        .transition(.scale.combined(with: .opacity))
                }

                // Letter column in glass container
                VStack(spacing: 0) {
                    ForEach(letters, id: \.self) { letter in
                        Text(letter)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(letter == selectedLetter ? Color.listenUpOrange : .primary)
                            .frame(height: letterHeight)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 4)
                .padding(.vertical, 8)
                .frame(width: 20)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let adjustedY = value.location.y - 8
                            handleDrag(at: adjustedY, letterHeight: letterHeight)
                        }
                        .onEnded { _ in
                            endDrag()
                        }
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
            .padding(.trailing, 4)
        }
        .opacity(isVisible || isDragging ? 1 : 0)
        .animation(.easeInOut(duration: 0.2), value: isVisible)
        .animation(.easeInOut(duration: 0.1), value: isDragging)
    }

    private func handleDrag(at y: CGFloat, letterHeight: CGFloat) {
        guard !letters.isEmpty, letterHeight > 0 else { return }

        if !isDragging {
            isDragging = true
            feedbackGenerator.prepare()
        }

        let index = Int(y / letterHeight)
        let clampedIndex = max(0, min(index, letters.count - 1))
        let letter = letters[clampedIndex]

        if letter != selectedLetter {
            selectedLetter = letter
            feedbackGenerator.impactOccurred()
            onLetterSelected(letter)
        }
    }

    private func endDrag() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            withAnimation(.easeOut(duration: 0.2)) {
                isDragging = false
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            selectedLetter = nil
        }
    }
}

// MARK: - Preview

#Preview("Loading") {
    NavigationStack {
        LibraryView()
    }
    .environment(CurrentUserObserver())
}
