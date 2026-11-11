package sbtcompat

import java.io.{ File, IOException }
import java.nio.file.{ Files, Path, StandardCopyOption }

private[sbtcompat] object WritableFiles {
  def withWritableFiles[A](files: Seq[File])(action: Seq[File] => A): A = {
    files.foreach { file =>
      val path = file.toPath
      if (!Files.isRegularFile(path)) throw new IOException(s"Not a regular file: $path")
      if (Files.isSymbolicLink(path)) {
        val copy = Files.createTempFile(path.toAbsolutePath.getParent, ".sbt-writable-", ".tmp")
        try {
          val input = Files.newInputStream(path)
          try Files.copy(input, copy, StandardCopyOption.REPLACE_EXISTING)
          finally input.close()
          makeWritable(copy)
          Files.move(copy, path, StandardCopyOption.REPLACE_EXISTING)
        } finally Files.deleteIfExists(copy)
      } else makeWritable(path)
    }
    action(files)
  }

  private def makeWritable(path: Path): Unit =
    if (!Files.isWritable(path) && (!path.toFile.setWritable(true) || !Files.isWritable(path)))
      throw new IOException(s"Cannot make file writable: $path")
}
