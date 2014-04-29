/*Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

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

object Dependencies {
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

  val scalatest = "org.scalatest" %% "scalatest" % "2.1.5"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.7"

  val lz4 = "net.jpountz.lz4" % "lz4" % "1.2.0"

  val async_http_client = "com.ning" % "async-http-client" % "1.8.8"

  val shapeless = "com.chuusai" %% "shapeless" % "2.0.0"

  object netty {
    object constants {
      val version = "4.0.18.Final"
      val name = "netty"
      val group = "io.netty"
    }
    import constants._
    val Seq(buffer, http) = Seq("buffer", "codec-http").map(a => group % s"$name-$a" % version)
  }
  object jackson {
    object constants {
      val version = "2.3.3"
      val name = "jackson"
      object group {
        val prefix = s"com.fasterxml.$name"
        val core = s"$prefix.core"
        val module = s"$prefix.module"
      }
      val module = s"$name-module"
    }
    import constants._
    val Seq(core, databind) = Seq("core", "databind").map(a => group.core % s"$name-$a" % version)
    val Seq(afterburner) = Seq("afterburner").map(a => group.module % s"$module-$a" % version)
  }
  object commons {
    val io = "commons-io" % "commons-io" % "2.4"
  }
  object dispatch {
    object constants {
      val version = "0.11.0"
      val name = "dispatch"
      val group = "net.databinder.dispatch"
    }
    import constants._
    val Seq(core) = Seq("core").map(a => group %% s"$name-$a" % version)
  }
  object akka {
    object constants {
      val version = "2.3.2"
      val name = "akka"
      val group = "com.typesafe.akka"
    }
    import constants._
    val Seq(actor) = Seq("actor").map(a => group %% s"$name-$a" % version)
  }
  object twitter4j {
    object constants {
      val version = "4.0.1"
      val name = "twitter4j"
      val group = "org.twitter4j"
    }
    import constants._
    val Seq(core, stream, async) = Seq("core", "stream", "async").map(a => group % s"$name-$a" % version)
  }
  object libraries {
    object constants {
      val test = "test"
    }
    import constants._
    private[this] def d = Dependencies
    val common = Seq(slf4j, commons.io, akka.actor, shapeless, scalatest % test, logback % test)
    val macros = common ++ Seq()
    val core = common ++ Seq(netty.buffer)
    val hashing = common ++ Seq(lz4 % test)
    val json = common ++ Seq(jackson.core, jackson.databind, jackson.afterburner)
    val `http-client` = common ++ Seq(async_http_client /* ensure minimum version */, dispatch.core)
    val `web-twitter` = common ++ Seq(twitter4j.core, twitter4j.stream, twitter4j.async)
  }
}
