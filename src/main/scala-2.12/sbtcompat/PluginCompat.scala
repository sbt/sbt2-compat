package sbtcompat

// sbt 2 -> sbt 1 compatibility
// Exposes (a subset of) the sbt 2 API using sbt 1 API.
// Follows the same pattern as sbt-compat (0.13 -> 1):
//   the "old" side (this file) does the heavy lifting,
//   the "new" side (scala-3/) is a thin stub.
//
// Implicit classes are used ONLY to add methods that sbt 2 types
// have natively but sbt 1 types lack. This eliminates those methods
// entirely from the scala-3 file. Everything else is a plain function
// in both files.

import java.io.File
import java.nio.file.{ Path => NioPath }
import java.io.{ BufferedInputStream, FileInputStream }
import java.security.{ DigestInputStream, MessageDigest }
import sbt._
import xsbti.FileConverter

object PluginCompat {

  // ============================================================
  // File / virtual file type aliases
  // ============================================================
  // In sbt 2, classpaths use HashedVirtualFileRef/VirtualFile/VirtualFileRef.
  // In sbt 1, everything is java.io.File.

  type FileRef = java.io.File
  type Out = java.io.File
  type ArtifactPath = java.io.File

  // ============================================================
  // Implicit enrichments
  // ============================================================
  // ONLY for methods that sbt 2 types have natively but sbt 1 types lack.
  // These are completely absent from the scala-3 file.

  /** Gives java.io.File the .name() method that sbt 2's
   *  HashedVirtualFileRef and VirtualFileRef have natively.
   *  (File only has .getName().)
   */
  implicit class FileRefOps(private val ref: File) extends AnyVal {
    def name(): String = ref.getName()
    def contentHashStr: String = {
      val md = MessageDigest.getInstance("SHA-256")
      val BufferSize = 8192
      val bis = new BufferedInputStream(new FileInputStream(ref))
      try {
        val dis = new DigestInputStream(bis, md)
        try {
          val buffer = new Array[Byte](BufferSize)
          while (dis.read(buffer) >= 0) {}
        } finally {
          dis.close()
        }
      } finally {
        bis.close()
      }
      s"sha256-${toHexString(md.digest)}"
    }
    def sizeBytes: Long = ref.length()
    private def toHexString(bytes: Array[Byte]): String = {
      val sb = new StringBuilder
      for {
        b <- bytes
      } { sb.append(f"${b & 0xff}%02x") }
      sb.toString
    }
  }

  /** Gives sbt.Def the .uncached() method that sbt 2 has natively.
   *  In sbt 2 all tasks are cached by default; Def.uncached(a) opts out.
   *  In sbt 1 there is no caching, so this is a no-op.
   */
  implicit class DefOps(private val singleton: Def.type) extends AnyVal {
    def uncached[A](a: A): A = a
  }

  // ============================================================
  // File conversions (identity on sbt 1, real work on sbt 2)
  // ============================================================

  def toFile(a: Attributed[File])(implicit conv: FileConverter): File =
    a.data
  def toFile(ref: File)(implicit conv: FileConverter): File =
    ref

  def toNioPath(a: Attributed[File])(implicit conv: FileConverter): NioPath =
    a.data.toPath()
  def toNioPath(ref: File)(implicit conv: FileConverter): NioPath =
    ref.toPath()

  def toOutput(x: File)(implicit conv: FileConverter): File =
    x
  def toFileRef(x: File)(implicit conv: FileConverter): FileRef =
    x
  def toArtifactPath(f: File)(implicit conv: FileConverter): ArtifactPath =
    f
  def artifactPathToFile(ref: File)(implicit conv: FileConverter): File =
    ref

  def toNioPaths(cp: Seq[Attributed[File]])(implicit conv: FileConverter): Vector[NioPath] =
    cp.map(_.data.toPath()).toVector
  def toFiles(cp: Seq[Attributed[File]])(implicit conv: FileConverter): Vector[File] =
    cp.map(_.data).toVector

  def toFileRefsMapping(mappings: Seq[(File, String)])(implicit conv: FileConverter): Seq[(FileRef, String)] =
    mappings

  def virtualFileRefToFile(f: File)(implicit conv: FileConverter): File =
    f
  def fileToVirtualFileRef(f: File)(implicit conv: FileConverter): File =
    f

  def toAttributedFiles(files: Seq[File])(implicit conv: FileConverter): Seq[Attributed[File]] =
    Attributed.blankSeq(files)

  // ============================================================
  // ModuleID / Artifact serialization
  // ============================================================
  // In sbt 2, stored as JSON strings (Keys.moduleIDStr / Keys.artifactStr);
  // parsing goes through Classpaths.moduleIdJsonKeyFormat etc.
  // In sbt 1, stored as typed AttributeKeys; identity pass-through.

  val moduleIDStr = Keys.moduleID.key
  val artifactStr = Keys.artifact.key

  def parseModuleIDStrAttribute(m: ModuleID): ModuleID = m
  def moduleIDToStr(m: ModuleID): ModuleID = m
  def parseArtifactStrAttribute(a: Artifact): Artifact = a
  def artifactToStr(art: Artifact): Artifact = art

  // ============================================================
  // Credentials
  // ============================================================
  // In sbt 2 the helpers moved to sbt.internal.librarymanagement.ivy.IvyCredentials.
  // In sbt 1 they live directly on sbt.Credentials.

  def toDirectCredentials(c: Credentials): DirectCredentials =
    Credentials.toDirect(c)

  def credentialForHost(cs: Seq[Credentials], host: String): Option[DirectCredentials] =
    Credentials.forHost(cs, host)

  // ============================================================
  // ScopedKey / Settings
  // ============================================================
  // In sbt 2, data.set takes a ScopedKey directly.
  // In sbt 1, data.set takes (scope, key) separately.

  def createScopedKey[T](settingKey: SettingKey[T], projRef: ProjectRef): ScopedKey[T] = {
    val scope = GlobalScope.copy(project = Select(projRef))
    Scoped.scopedSetting(scope, settingKey.key).scopedKey
  }

  def setSetting[T](
    data: sbt.internal.util.Settings[Scope],
    scopedKey: ScopedKey[T],
    value: T
  ): sbt.internal.util.Settings[Scope] =
    data.set(scopedKey.scope, scopedKey.key, value)

  // ============================================================
  // Attributed file helpers
  // ============================================================
  // In sbt 2, Attributed uses StringAttributeKey (string-based storage).
  // In sbt 1, Attributed uses typed AttributeKey directly.

  def attributedPutFile[T](a: Attributed[T], key: AttributeKey[File], value: File): Attributed[T] =
    a.put(key, value)

  def attributedGetFile[T](a: Attributed[T], key: AttributeKey[File]): Option[File] =
    a.get(key)

  def attributedPutFiles[T](a: Attributed[T], key: AttributeKey[Seq[File]], value: Seq[File]): Attributed[T] =
    a.put(key, value)

  def attributedGetFiles[T](a: Attributed[T], key: AttributeKey[Seq[File]]): Option[Seq[File]] =
    a.get(key)

  def attributedPutValue[T, V](a: Attributed[T], key: AttributeKey[V], value: V): Attributed[T] =
    a.put(key, value)
}
