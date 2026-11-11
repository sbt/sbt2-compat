package sbtcompat

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime
import org.scalatest.flatspec.AnyFlatSpec

class WritableFilesTest extends AnyFlatSpec {
  private def withDirectory(test: Path => Unit): Unit = {
    val directory = Files.createTempDirectory("sbt-writable-test-")
    try test(directory)
    finally sbt.io.IO.delete(directory.toFile)
  }

  "withWritableFiles" should "prepare all supplied paths before calling the action and preserve cached targets" in {
    withDirectory { directory =>
      val cached = Files.write(directory.resolve("cached"), "original".getBytes(UTF_8))
      assert(cached.toFile.setWritable(false))
      val permissions = Files.getPosixFilePermissions(cached)
      val first = Files.createSymbolicLink(directory.resolve("first"), cached.getFileName)
      val second = Files.createSymbolicLink(directory.resolve("second"), cached)
      val regular = Files.write(directory.resolve("regular"), Array[Byte](1))
      val modified = FileTime.fromMillis(1000000000000L)
      Files.setLastModifiedTime(regular, modified)
      val files = Seq(first.toFile, regular.toFile, second.toFile)
      val result = PluginCompat.withWritableFiles(files) { prepared =>
        assert(prepared eq files)
        assert(prepared.forall(f => !Files.isSymbolicLink(f.toPath) && Files.isWritable(f.toPath)))
        assert(Files.getLastModifiedTime(regular) == modified)
        assert(!Files.isSameFile(first, cached))
        assert(!Files.isSameFile(first, second))
        Files.write(first, "changed".getBytes(UTF_8))
        assert(new String(Files.readAllBytes(second), UTF_8) == "original")
        42
      }
      assert(result == 42)
      assert(new String(Files.readAllBytes(cached), UTF_8) == "original")
      assert(Files.getPosixFilePermissions(cached) == permissions)
    }
  }

  it should "make read-only regular files writable and leave writable regular files in place" in {
    withDirectory { directory =>
      val file = Files.write(directory.resolve("regular"), Array[Byte](1))
      val alias = Files.createLink(directory.resolve("alias"), file)
      assert(file.toFile.setWritable(false))
      PluginCompat.withWritableFiles(Seq(file.toFile)) { files =>
        assert(Files.isWritable(files.head.toPath))
        assert(Files.isSameFile(file, alias))
      }
    }
  }

  it should "reject missing files, directories, and dangling links before calling the action" in {
    withDirectory { directory =>
      val missing = directory.resolve("missing")
      val dangling = Files.createSymbolicLink(directory.resolve("dangling"), missing)
      Seq(missing, directory, dangling).foreach { invalid =>
        var called = false
        intercept[IOException] {
          PluginCompat.withWritableFiles(Seq(invalid.toFile)) { _ => called = true }
        }
        assert(!called)
      }
    }
  }

  it should "invoke the action for an empty sequence and propagate action failures" in {
    val empty = Seq.empty[java.io.File]
    assert(PluginCompat.withWritableFiles(empty)(files => files eq empty))
    withDirectory { directory =>
      val cached = Files.write(directory.resolve("cached"), Array[Byte](1))
      val link = Files.createSymbolicLink(directory.resolve("link"), cached)
      val failure = new IllegalStateException("action failed")
      assert(intercept[IllegalStateException] {
        PluginCompat.withWritableFiles(Seq(link.toFile)) { _ => throw failure }
      } eq failure)
      assert(!Files.isSymbolicLink(link))
      assert(Files.isWritable(link))
    }
  }
}
