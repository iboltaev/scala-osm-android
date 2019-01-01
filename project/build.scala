import sbt._
import android.Keys._
import Keys._


object Build extends android.AutoBuild {

  import Dependencies._

  val resolvers = Seq(
    "Maven central" at "http://oss.sonatype.org/content/repositories/releases",
    "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases/"   
  )

  val buildSettings = android.Plugin.androidBuild ++
  Seq(libraryDependencies ++= Scaloid ++ Rx ++ HttpBlaze ++ Scalatest,
      scalaVersion := "2.11.8",
      externalResolvers := Resolver.withDefaultResolvers(resolvers),
      javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
      scalacOptions ++= Seq("-feature","-target:jvm-1.7"),
      dexMulti in Android := true,
      proguardOptions in Android ++= Seq("@project/proguard.cfg"),
      useProguard in Android := true,
      apkbuildExcludes in Android ++= Seq("reference.conf"),
      sourceGenerators in Compile := Seq((sourceGenerators in Compile).value.last)
    )

  val main = Project(
    "ScalaMap",
    file("."),
    settings = buildSettings)
}



object Dependencies {

  import Versions._

  val Scaloid = Seq(
    "org.scaloid" %% "scaloid" % ScaloidVersion
  )	

  val Rx = Seq(
    "io.reactivex" %% "rxscala" % "0.26.5"
  )

  val Scalatest = Seq("org.scalatest" %% "scalatest" % "3.0.5" % "test")

  val HttpBlaze = Seq("org.http4s" %% "blaze-http" % "0.14.0-M5")

  object Versions {
    val ScaloidVersion = "4.2"
  }

}
