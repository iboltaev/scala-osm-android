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
    Seq(libraryDependencies ++= Akka ++ Scaloid,
	      externalResolvers := Resolver.withDefaultResolvers(resolvers),
          javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
          scalacOptions ++= Seq("-feature","-target:jvm-1.7"),
          dexMulti in Android := true,

	      proguardOptions in Android ++= Seq("@project/proguard.cfg"),
	      useProguard in Android := true,
	      apkbuildExcludes in Android ++= Seq("reference.conf")
    )

  val main = Project(
    "ScalaMap",
    file("."),
    settings = buildSettings)


}



object Dependencies {

  import Versions._

  val Akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.5",
    "com.typesafe" % "config" % "1.0.2"
  )

  val Scaloid = Seq(
    "org.scaloid" %% "scaloid" % ScaloidVersion
  )	


  object Versions {
    val AkkaVersion = "2.3.3"
    val ScaloidVersion = "3.3-8"
  }

}




