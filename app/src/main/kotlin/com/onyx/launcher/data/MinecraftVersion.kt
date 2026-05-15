package com.onyx.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class VersionType(val id: String) {
    RELEASE("release"), SNAPSHOT("snapshot"), OLD_BETA("old_beta"), OLD_ALPHA("old_alpha");
    companion object { fun fromId(id: String) = entries.find { it.id == id } ?: RELEASE }
}

@Serializable
data class VersionInfo(val id: String, val type: String, val url: String, val time: String, val releaseTime: String, val sha1: String? = null) {
    val versionType: VersionType get() = VersionType.fromId(type)
}

@Serializable
data class VersionManifest(val latest: LatestVersions, val versions: List<VersionInfo>)

@Serializable
data class LatestVersions(val release: String, val snapshot: String)

@Serializable
data class VersionJson(
    val id: String, val type: String? = null, val mainClass: String,
    val minecraftArguments: String? = null, val arguments: Arguments? = null,
    val assets: String? = null, val assetIndex: AssetIndex? = null,
    val downloads: Downloads? = null, val libraries: List<Library> = emptyList(),
    val javaVersion: JavaVersion? = null, val inheritsFrom: String? = null
)

@Serializable data class Arguments(val game: List<String>? = null, val jvm: List<String>? = null)
@Serializable data class AssetIndex(val id: String, val sha1: String, val size: Long, val url: String)
@Serializable data class Downloads(val client: DownloadInfo? = null, val server: DownloadInfo? = null)
@Serializable data class DownloadInfo(val sha1: String, val size: Long, val url: String)
@Serializable data class Library(val name: String, val downloads: LibraryDownloads? = null, val rules: List<Rule>? = null, val natives: Map<String, String>? = null, val extract: Extract? = null)
@Serializable data class LibraryDownloads(val artifact: LibraryArtifact? = null, val classifiers: Map<String, LibraryArtifact>? = null)
@Serializable data class LibraryArtifact(val path: String, val sha1: String, val size: Long, val url: String)
@Serializable data class Rule(val action: String, val os: OsRule? = null)
@Serializable data class OsRule(val name: String? = null)
@Serializable data class Extract(val exclude: List<String>? = null)
@Serializable data class JavaVersion(val component: String, val majorVersion: Int)
