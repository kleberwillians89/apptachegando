package org.example.project

class WasmPlatform : Platform {
    override val name: String = "Web (Wasm)"
}

actual fun getPlatform(): Platform = WasmPlatform()
