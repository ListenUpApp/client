package com.calypsan.listenup.client.core

/** Desktop builds read `-Dlistenup.debug=true` at launch; otherwise release-mode. */
actual val isDebugBuild: Boolean = System.getProperty("listenup.debug") == "true"
