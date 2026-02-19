name := "sbt2-compat"
organization := "com.github.sbt"
description := "A compatibility plugin; provides a unified API for sbt plugins cross-building for sbt 1.x and sbt 2.x"

def scala212 = "2.12.21"
def scala3 = "3.8.1"
scalaVersion := scala3
crossScalaVersions := Seq(scala3, scala212)

enablePlugins(SbtPlugin)

(pluginCrossBuild / sbtVersion) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.5.8"
    case _      => "2.0.0-RC9"
  }
}

Compile / scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" => Seq("-Xsource:3", "-feature", "-unchecked", "-release:8")
    case _      => Seq("-feature", "-unchecked")
  }
}

// Release configuration
publishMavenStyle := true
licenses := Seq(License.Apache2)
homepage := Some(url("https://github.com/sbt/sbt2-compat"))
scmInfo := Some(
  ScmInfo(url("https://github.com/sbt/sbt2-compat"), "scm:git@github.com:sbt/sbt2-compat.git")
)
developers := List(
  Developer(
    id = "anatoliykmetyuk",
    name = "Anatolii Kmetiuk",
    email = "anatoliikmt@proton.me",
    url = url("https://github.com/anatoliykmetyuk")
  )
)
