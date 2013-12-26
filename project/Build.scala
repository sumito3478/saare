/*Copyright 2013 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
import sbt._
import Keys._

object Build extends Build {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform
  lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
    SbtScalariform.ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, true))

  lazy val commonSettings = Seq(
    version := "0.0.1-SNAPSHOT",
    javaOptions := Seq("-Xms1024m"),
    organization := "info.sumito3478",
    scalaVersion := "2.11.0-M7",
    crossScalaVersions := Seq("2.10.3", "2.11.0-M7"),
    fork := true,
    resolvers += Resolver.sonatypeRepo("releases"),
    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.0-M1" cross CrossVersion.full),
    pomExtra := (
      <licenses>
        <name>The Apache Software License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </licenses>
      ),
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-target:jvm-1.7",
      "-deprecation",
      "-feature",
      "-unchecked")) ++ scalariformSettings

  val common = (p: Project) =>
    p.copy(id = s"saare-${p.id}")
      .settings(commonSettings: _*)

  implicit class ProjectW(val self: Project) extends AnyVal {
    def libs(xs: Seq[ModuleID]) = self.settings(libraryDependencies ++= xs)
  }

  import Dependencies._

  lazy val `core-macros` = project configure common libs libraries.macros

  lazy val core = project configure common libs libraries.core dependsOn `core-macros`

  lazy val json = project configure common libs libraries.json dependsOn core

  lazy val http = project configure common libs libraries.http dependsOn core

  lazy val root = project in file(".") configure common libs libraries.common aggregate (`core-macros`, core, json, http) settings (publishArtifact := false)
}
