package sbtcompat

import sbt._
import sbt.Keys._
import sbtcompat.PluginCompat.{ DefOps, FileRef }

/** Compile-only regression test for https://github.com/sbt/sbt2-compat/issues/16.
 *
 *  DefOps must be explicitly importable on both Scala versions so FileRefOps can
 *  remain excluded while .name() and Def.uncached are used in the same scope.
 */
object Issue16CompileTest extends AutoPlugin {
  object autoImport {
    val issue16Task = taskKey[Unit]("Issue #16 compile regression test")
  }

  import autoImport._

  def nameOf(ref: FileRef): String = ref.name

  override lazy val projectSettings = Seq(
    issue16Task := Def.uncached {
      ()
    }
  )
}
