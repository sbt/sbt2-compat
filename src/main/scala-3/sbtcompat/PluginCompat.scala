package sbtcompat

// sbt 2 stub — minimal because sbt 2 already has the target API.
// The sbt 1 tree (scala-2.12/) is the workhorse.
//
// Methods that sbt 2 types have natively are NOT defined here:
//   - .name() is native on HashedVirtualFileRef / VirtualFileRef
//   - Def.uncached is native in sbt 2

import java.io.File
import java.nio.file.{ Path => NioPath }
import sbt.*
import sbt.internal.util.StringAttributeKey
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile, VirtualFileRef }

object PluginCompat:

  type FileRef = HashedVirtualFileRef
  type Out = VirtualFile
  type ArtifactPath = VirtualFileRef

  // --- File conversions ---

  def toFile(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): File =
    conv.toPath(a.data).toFile()
  def toFile(ref: HashedVirtualFileRef)(using conv: FileConverter): File =
    conv.toPath(ref).toFile()

  def toNioPath(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): NioPath =
    conv.toPath(a.data)
  def toNioPath(ref: HashedVirtualFileRef)(using conv: FileConverter): NioPath =
    conv.toPath(ref)

  def toOutput(x: File)(using conv: FileConverter): VirtualFile =
    conv.toVirtualFile(x.toPath())
  def toFileRef(x: File)(using conv: FileConverter): FileRef =
    conv.toVirtualFile(x.toPath())
  def toArtifactPath(f: File)(using conv: FileConverter): ArtifactPath =
    conv.toVirtualFile(f.toPath())
  def artifactPathToFile(ref: VirtualFileRef)(using conv: FileConverter): File =
    conv.toPath(ref).toFile()

  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): Vector[NioPath] =
    cp.map(a => conv.toPath(a.data)).toVector
  def toFiles(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): Vector[File] =
    toNioPaths(cp).map(_.toFile())

  def toFileRefsMapping(mappings: Seq[(File, String)])(using conv: FileConverter): Seq[(FileRef, String)] =
    mappings.map { case (f, name) => toFileRef(f) -> name }

  def virtualFileRefToFile(ref: VirtualFileRef)(using conv: FileConverter): File =
    conv.toPath(ref).toFile()
  def fileToVirtualFileRef(f: File)(using conv: FileConverter): VirtualFileRef =
    conv.toVirtualFile(f.toPath())

  def toAttributedFiles(files: Seq[File])(using conv: FileConverter): Seq[Attributed[HashedVirtualFileRef]] =
    Attributed.blankSeq(files.map(f => conv.toVirtualFile(f.toPath())))

  // --- ModuleID / Artifact serialization ---

  val moduleIDStr = Keys.moduleIDStr
  val artifactStr = Keys.artifactStr

  def parseModuleIDStrAttribute(str: String): ModuleID = Classpaths.moduleIdJsonKeyFormat.read(str)
  def moduleIDToStr(m: ModuleID): String = Classpaths.moduleIdJsonKeyFormat.write(m)

  def parseArtifactStrAttribute(str: String): Artifact =
    import sbt.librarymanagement.LibraryManagementCodec.ArtifactFormat
    import sjsonnew.support.scalajson.unsafe.*
    Converter.fromJsonUnsafe[Artifact](Parser.parseUnsafe(str))

  def artifactToStr(art: Artifact): String =
    import sbt.librarymanagement.LibraryManagementCodec.ArtifactFormat
    import sjsonnew.support.scalajson.unsafe.*
    CompactPrinter(Converter.toJsonUnsafe(art))

  // --- Credentials ---

  def toDirectCredentials(c: Credentials) =
    import sbt.internal.librarymanagement.ivy.IvyCredentials
    IvyCredentials.toDirect(c)

  def credentialForHost(cs: Seq[Credentials], host: String) =
    import sbt.internal.librarymanagement.ivy.IvyCredentials
    IvyCredentials.forHost(cs, host)

  // --- ScopedKey / Settings ---

  def createScopedKey[T](settingKey: SettingKey[T], projRef: ProjectRef): ScopedKey[T] =
    val scope = GlobalScope.copy(project = Select(projRef))
    Scoped.scopedSetting(scope, settingKey.key).scopedKey

  def setSetting[T](data: Def.Settings, scopedKey: ScopedKey[T], value: T): Def.Settings =
    data.set(scopedKey, value)

  // --- Attributed file helpers ---

  private val FilePathSeparator = "\u0000"

  def attributedPutFile[T](a: Attributed[T], key: AttributeKey[File], value: File): Attributed[T] =
    a.put(StringAttributeKey(key.label), value.getAbsolutePath)

  def attributedGetFile[T](a: Attributed[T], key: AttributeKey[File]): Option[File] =
    a.get(StringAttributeKey(key.label)).map(path => new File(path))

  def attributedPutFiles[T](a: Attributed[T], key: AttributeKey[Seq[File]], value: Seq[File]): Attributed[T] =
    a.put(StringAttributeKey(key.label), value.map(_.getAbsolutePath).mkString(FilePathSeparator))

  def attributedGetFiles[T](a: Attributed[T], key: AttributeKey[Seq[File]]): Option[Seq[File]] =
    a.get(StringAttributeKey(key.label))
      .map: s =>
        if s.isEmpty then Nil else s.split(FilePathSeparator).toSeq.map(new File(_))

  def attributedPutValue[T, V](a: Attributed[T], key: AttributeKey[V], value: V): Attributed[T] =
    a.put(StringAttributeKey(key.label), value.toString)

end PluginCompat
