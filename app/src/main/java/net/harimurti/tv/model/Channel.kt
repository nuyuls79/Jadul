package net.harimurti.tv.model

import com.google.gson.annotations.SerializedName

class Channel {
    var name: String? = null
    
    @SerializedName("logo_url")
    var logoUrl: String? = null
    
    @SerializedName("stream_url")
    var streamUrl: String? = null
    
    @SerializedName("drm_id")
    var drmId: String? = null
    
    @SerializedName("user_agent")
    var userAgent: String? = null
    
    @SerializedName("referer")
    var referer: String? = null
    
    // Tambahkan properti 'logo' untuk kompatibilitas dengan adapter
    val logo: String?
        get() = logoUrl
    
    // Tambahkan properti 'url' untuk kompatibilitas dengan adapter
    val url: String?
        get() = streamUrl
    
    // Tambahkan properti 'group' jika diperlukan (bisa diisi dari kategori)
    var group: String? = null
}