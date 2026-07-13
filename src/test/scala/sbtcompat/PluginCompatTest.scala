package sbtcompat

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sbt.internal.inc.MappedFileConverter

import PluginCompat._

class PluginCompatTest extends AnyFlatSpec with Matchers {
  val roots = Map.empty[String, java.nio.file.Path]
  implicit val conv: xsbti.FileConverter = MappedFileConverter(roots, true)

  "PluginCompat" should "calculate digests on FileRefs" in {
    val f: FileRef = toFileRef(sbt.file("./src/test/resources/test.txt"))
    f.contentHashStr should be("sha256-ad3756b9a126e596c92f77dbd1a432a00cc3af4fa34943dfc3101d58638cbdde")
    f.sizeBytes should be(32)
  }
}
