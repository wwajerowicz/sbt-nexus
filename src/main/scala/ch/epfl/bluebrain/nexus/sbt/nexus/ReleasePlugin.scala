package ch.epfl.bluebrain.nexus.sbt.nexus

import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease._

/**
  * Release configuration for projects using the plugin.  The versions used in the release can be overridden by means
  * of environment variables, specifically ''RELEASE_VERSION'' and ''NEXT_VERSION''.
  *
  * If the ''RELEASE_VERSION'' is omitted its value will be computed by striping the ''-SNAPSHOT'' suffix of the
  * current version (as listed in ''version.sbt'').
  *
  * If the ''NEXT_VERSION'' property is its value will be computed based on the resulting value of the
  * ''RELEASE_VERSION'' and the default bump strategy configured by ''releaseVersionBump'' (release + bump).
  */
object ReleasePlugin extends AutoPlugin {

  lazy val buildInfoJson  = taskKey[File]("Generates build info json")

  override lazy val requires = sbtrelease.ReleasePlugin

  override lazy val trigger = allRequirements

  override lazy val projectSettings = Seq(
    buildInfoJson := {
      val file = target.value / "buildinfo.json"
      val v = version.value
      val major = v.split("\\.")(0)
      val minor = v.split("\\.")(1)
      val contributors = ("git shortlog -sne HEAD" #| "cut -f2" !!) split "\n"
      val contents =
        s"""{
           |"name": "nexus-${name.value}",
           |"version": "$v",
           |"version_major": "$major",
           |"version_minor": "$minor",
           |"description"  : "${description.value}",
           |"repository"   :  {
           |    "url": "${homepage.value.map(_.toString).getOrElse("undefined")}",
           |    "issuesurl": "${homepage.value.map(_.toString + "/issues").getOrElse("undefined")}"
           |
           |  },
           |"license": "${licenses.value.map(_._1).mkString(",")}",
           |"author": "Blue Brain Nexus Team",
           |"contributors": [${contributors.map(c => s"\042$c\042").mkString(",")}]
           |}
         """.stripMargin
      IO.write(file, contents)
      file
    },
    // bump the patch (bugfix) version by default
    releaseVersionBump   := Version.Bump.Bugfix,
    // compute the version to use for the release (from sys.env or version.sbt)
    releaseVersion       := { ver =>
      sys.env.get("RELEASE_VERSION")                                // fetch the optional system env var
        .map(_.trim)
        .filterNot(_.isEmpty)
        .map(v => Version(v).getOrElse(versionFormatError))         // parse it into a version or throw
        .orElse(Version(ver).map(_.withoutQualifier))               // fallback on the current version without a qualifier
        .map(_.string)                                              // map it to its string representation
        .getOrElse(versionFormatError)                              // throw if we couldn't compute the version
    },
    // compute the next development version to use for the release (from sys.env or release version)
    releaseNextVersion   := { ver =>
      sys.env.get("NEXT_VERSION")                                   // fetch the optional system env var
        .map(_.trim)
        .filterNot(_.isEmpty)
        .map(v => Version(v).getOrElse(versionFormatError))         // parse it into a version or throw
        .orElse(Version(ver).map(_.bump(releaseVersionBump.value))) // fallback on the current version bumped accordingly
        .map(_.asSnapshot.string)                                   // map it to its snapshot version as string
        .getOrElse(versionFormatError)                              // throw if we couldn't compute the version
    },
    // never cross build
    releaseCrossBuild    := false,
    // tag the release with the '$artifactId-$version'
    releaseTagName       := s"v${(version in ThisBuild).value}",
    // tag commit comment
    releaseTagComment    := s"Releasing version ${(version in ThisBuild).value}",
    // the message to use when committing the new version to version.sbt
    releaseCommitMessage := s"Setting new version to ${(version in ThisBuild).value}",
    // the default release process, listed explicitly
    releaseProcess       := Seq(
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepTask(buildInfoJson),
      setNextVersion,
      commitNextVersion,
      pushChanges))
}
