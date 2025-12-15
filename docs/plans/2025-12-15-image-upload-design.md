# Image Upload Design

## Overview

Enable users to upload cover images for books and profile photos for contributors from the mobile app. Images are picked from the device gallery, uploaded to the server, and stored in the app data folder.

## Scope

- **Book covers**: Upload via Book Edit screen
- **Contributor photos**: Upload via Contributor Edit screen
- **Image source**: Gallery only (no camera)
- **Processing**: Server-side (client sends full resolution)

## Server Changes

### Contributor Image Storage

Mirror the existing `images.Storage` pattern. Create a new storage instance for contributor images:

```
{METADATA_PATH}/contributors/{contributorID}.jpg
```

Options:
1. Create a second `images.Storage` instance with different base path
2. Make `Storage` more generic by accepting a subdirectory parameter

Recommendation: Option 2 - generalize `Storage` to accept a subdirectory, avoiding code duplication.

### New Endpoints

**Upload Contributor Image**

```
PUT /api/v1/contributors/{id}/image
Content-Type: multipart/form-data
Field: "file"

Response: { "image_url": "/api/v1/contributors/{id}/image" }
```

Implementation follows `handleUploadCover` pattern:
- Validate auth + ACL
- Parse multipart form (10MB limit)
- Validate magic bytes (JPEG, PNG, WebP, GIF)
- Save to contributor storage
- Update contributor's `ImagePath` field

**Serve Contributor Image**

```
GET /api/v1/contributors/{id}/image
```

Serves the contributor image with caching headers (ETag, Last-Modified) - same pattern as cover serving.

## Client API Layer

### New Contract Methods

Add to `ListenUpApiContract`:

```kotlin
suspend fun uploadBookCover(
    bookId: String,
    imageData: ByteArray,
    filename: String,
): Result<UploadImageResponse>

suspend fun uploadContributorImage(
    contributorId: String,
    imageData: ByteArray,
    filename: String,
): Result<UploadImageResponse>
```

### Response Model

```kotlin
data class UploadImageResponse(
    val imageUrl: String,
)
```

### Implementation

Uses Ktor's `submitFormWithBinaryData` for multipart uploads:

```kotlin
override suspend fun uploadBookCover(
    bookId: String,
    imageData: ByteArray,
    filename: String,
): Result<UploadImageResponse> = withAuth {
    client.submitFormWithBinaryData(
        url = "books/$bookId/cover",
        formData = formData {
            append("file", imageData, Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=$filename")
                append(HttpHeaders.ContentType, "image/*")
            })
        }
    )
}
```

## Client Platform Layer

### Cross-Platform Interface

New expect/actual in `shared`:

```kotlin
// commonMain
expect class ImagePicker {
    suspend fun pickImage(): ImagePickerResult?
}

sealed class ImagePickerResult {
    data class Success(
        val data: ByteArray,
        val filename: String,
    ) : ImagePickerResult()

    data class Error(val message: String) : ImagePickerResult()
}
```

### Android Implementation (API 32+)

Uses modern Photo Picker with fallback for devices without it:

```kotlin
actual class ImagePicker(private val activity: ComponentActivity) {

    // Check if modern Photo Picker is available (API 33+ or Play Services backport)
    private val isPhotoPickerAvailable =
        ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(activity)

    // Modern picker
    private val modernLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> handleResult(uri) }

    // Fallback for API 32 without Play Services backport
    private val legacyLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> handleResult(uri) }

    actual suspend fun pickImage(): ImagePickerResult? =
        suspendCancellableCoroutine { cont ->
            continuation = cont

            if (isPhotoPickerAvailable) {
                modernLauncher.launch(PickVisualMediaRequest(ImageOnly))
            } else {
                legacyLauncher.launch("image/*")
            }
        }

    private fun handleResult(uri: Uri?) {
        uri?.let {
            val result = readImageFromUri(activity, it)
            continuation?.resume(result)
        } ?: continuation?.resume(null)
    }
}
```

No storage permissions required - both approaches use system picker.

### iOS Implementation (iOS 17+)

Uses modern PhotosUI SwiftUI `PhotosPicker`:

```swift
import PhotosUI
import SwiftUI

class ImagePickerController: ObservableObject {
    @Published var selectedItem: PhotosPickerItem?

    func pickImage() async -> ImagePickerResult? {
        guard let item = selectedItem else { return nil }

        do {
            if let data = try await item.loadTransferable(type: Data.self) {
                return ImagePickerResult.Success(
                    data: data,
                    filename: "image.jpg"
                )
            }
        } catch {
            return ImagePickerResult.Error(message: error.localizedDescription)
        }
        return nil
    }
}
```

Uses `Transferable` protocol for clean async data loading.

### Dependency Injection

Provide `ImagePicker` via Koin, injected into ViewModels.

## UI Integration

### Book Edit Screen

Make the cover image tappable in `IdentityHeader`:

```kotlin
ElevatedCard(
    onClick = { viewModel.onEvent(BookEditUiEvent.PickCoverImage) },
) {
    Box {
        AsyncImage(model = state.coverUrl, ...)

        // Subtle edit indicator
        Icon(
            Icons.Default.CameraAlt,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(scrim)
                .padding(8.dp)
        )
    }
}
```

### Contributor Edit Screen

Same pattern - make the circular avatar tappable:

```kotlin
ElevatedCard(
    onClick = { viewModel.onEvent(ContributorEditUiEvent.PickImage) },
    shape = CircleShape,
) {
    // Existing avatar display + edit indicator overlay
}
```

### ViewModel Integration

```kotlin
// New events
sealed class BookEditUiEvent {
    // ... existing events
    object PickCoverImage : BookEditUiEvent()
}

sealed class ContributorEditUiEvent {
    // ... existing events
    object PickImage : ContributorEditUiEvent()
}

// New state fields
data class BookEditUiState(
    // ... existing fields
    val isUploadingImage: Boolean = false,
)

// Handler in ViewModel
fun onEvent(event: BookEditUiEvent) {
    when (event) {
        is BookEditUiEvent.PickCoverImage -> {
            viewModelScope.launch {
                imagePicker.pickImage()?.let { result ->
                    when (result) {
                        is ImagePickerResult.Success -> uploadCover(result)
                        is ImagePickerResult.Error -> showError(result.message)
                    }
                }
            }
        }
    }
}

private suspend fun uploadCover(image: ImagePickerResult.Success) {
    _state.update { it.copy(isUploadingImage = true) }

    api.uploadBookCover(bookId, image.data, image.filename)
        .onSuccess { response ->
            _state.update { it.copy(
                coverUrl = response.imageUrl,
                isUploadingImage = false,
            ) }
        }
        .onFailure { error ->
            _state.update { it.copy(
                error = "Failed to upload cover",
                isUploadingImage = false,
            ) }
        }
}
```

### Loading State

Show a circular progress indicator overlay on the image while uploading.

## File Structure

```
server/
  internal/
    api/
      contributors_handlers.go  # Add handleUploadContributorImage, handleGetContributorImage
      server.go                 # Register new routes
    media/images/
      storage.go                # Generalize to support subdirectories

client/
  shared/src/
    commonMain/kotlin/.../
      data/remote/
        ApiContracts.kt         # Add upload methods to contract
        ListenUpApi.kt          # Implement upload methods
      domain/imagepicker/
        ImagePicker.kt          # expect class + result types
      presentation/
        bookedit/
          BookEditViewModel.kt  # Add pick/upload handling
        contributoredit/
          ContributorEditViewModel.kt  # Add pick/upload handling
    androidMain/kotlin/.../
      domain/imagepicker/
        ImagePicker.android.kt  # actual implementation
    iosMain/kotlin/.../
      domain/imagepicker/
        ImagePicker.ios.kt      # actual implementation
  composeApp/src/androidMain/kotlin/.../
    features/
      bookedit/
        BookEditScreen.kt       # Add tap handler + loading state
      contributoredit/
        ContributorEditScreen.kt # Add tap handler + loading state
```

## Testing Strategy

### Server
- Unit tests for new handlers (mirror existing `TestHandleUploadCover_*` patterns)
- Test invalid image formats, missing files, unauthorized access

### Client
- Unit tests for ViewModel upload flow (mock API, verify state transitions)
- Integration test for multipart form construction

## SSE Real-Time Sync

Image changes automatically sync via existing SSE infrastructure:

### How It Works

**Book Covers:**
1. Client uploads cover via `PUT /api/v1/books/{id}/cover`
2. Handler sets `book.CoverImage = &ImageFileInfo{...}`
3. Handler calls `store.UpdateBook(ctx, book)`
4. Store emits `book.updated` event via SSE with enriched `dto.Book`
5. All connected clients receive the event and update their local cache/UI

**Contributor Images:**
1. Client uploads image via `PUT /api/v1/contributors/{id}/image`
2. Handler sets `contributor.ImageURL = "/api/v1/contributors/{id}/image"`
3. Handler calls `store.UpdateContributor(ctx, contributor)`
4. Store emits `contributor.updated` event via SSE with `domain.Contributor`
5. All connected clients receive the event and update their local cache/UI

### Client-Side Handling

The client already listens to SSE events via `SyncManager`. When `book.updated` or `contributor.updated` events arrive:

1. Update the local database with new entity data
2. Invalidate any cached images for that entity
3. UI recomposes with new `coverUrl` / `imageUrl` from state

No additional SSE code required — the existing sync infrastructure handles it.

### Data Flow

```
[Client A uploads] → [Server saves + emits SSE] → [Client B receives + updates]
                                                 → [Client A receives + confirms]
```

## Future Considerations

- Audio file uploads for new books (different storage, metadata extraction)
- Image cropping before upload
- Camera capture option
