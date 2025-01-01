data class Profile(
    val inheritsFrom: String,
    val releaseTime: String,
    val mainClass: String,
    val libraries: List<Library>,
    val arguments: Arguments,
    val id: String,
    val time: String,
    val type: String
)

data class Library(
    val sha1: String?,
    val sha256: String?,
    val sha512: String?,
    val md5: String?,

    val name: String,
    val url: String,
)

data class Arguments(
    val jvm: List<String>,
    val game: List<String>
)


data class LibraryChecksums(
    val sha1: String?,
    val sha256: String?,
    val sha512: String?,
    val md5: String?,
)