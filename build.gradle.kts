// Версии подобраны как проверенная связка (AGP 8.13.2 требует Gradle >= 8.13, JDK 17).
// Compose 1.12+ требует compileSdk 37 и AGP 9.1+, поэтому здесь намеренно BOM 2025.08.00.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
