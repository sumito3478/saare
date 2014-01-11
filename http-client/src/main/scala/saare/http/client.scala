/*Copyright 2014 sumito3478 <sumito3478@gmail.com>

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
package saare
package http
package client

import scala.language.implicitConversions

import scala.concurrent._

import com.ning.http.{ client => ahc }

import saare._, Saare._
import saare.json._, Json._

object ProxyServer {
  object Protocol {
    case object Http extends Protocol
    case object Https extends Protocol
  }
  import Protocol._
  sealed trait Protocol {
    private[client] def toUnderlying = this match {
      case Http => ahc.ProxyServer.Protocol.HTTP
      case Https => ahc.ProxyServer.Protocol.HTTPS
    }
  }
}
case class Credential(user: String, password: String)
case class ProxyServer(protocol: ProxyServer.Protocol, host: String, port: Int, credential: Option[Credential]) {
  private[client] def toUnderlying = credential match {
    case Some(credential) => new ahc.ProxyServer(protocol.toUnderlying, host, port, credential.user, credential.password)
    case None => new ahc.ProxyServer(protocol.toUnderlying, host, port)
  }
}
class Request(private[client] val underlying: dispatch.Req)
object Request {
  private[this] implicit def request2underlying(request: Request): dispatch.Req = request.underlying
  private[this] implicit def underlying2request(underlying: dispatch.Req): Request = new Request(underlying = underlying)
  type Verb = Request => Request
  def HEAD: Verb = _.HEAD
  def GET: Verb = _.GET
  def POST: Verb = _.POST
  def PUT: Verb = _.PUT
  def DELETE: Verb = _.DELETE
  def PATCH: Verb = _.PATCH
  def TRACE: Verb = _.TRACE
  def OPTIONS: Verb = _.OPTIONS
  def segment(x: String): Verb = _ / x
  def secure: Verb = _.secure
  def headers(xs: (String, String)*): Verb = _ <:< xs
  def params(xs: (String, String)*): Verb = _ << xs
  def stringBody(x: String): Verb = _ << x
  def fileBody(x: java.io.File): Verb = _ <<< x
  def queries(xs: (String, String)*): Verb = _ <<? xs
  def followRedirects(x: Boolean): Verb = _ setFollowRedirects x
  def proxy(x: ProxyServer): Verb = _ setProxyServer x.toUnderlying

  def apply(url: String) = new Request(dispatch.url(url))
}
trait Handler[A] {
  private[client] def underlying: Either[ahc.Response => A, ahc.AsyncHandler[A]]
}
class Client(userAgent: Option[String] = None) extends Disposable[Client] {
  private[this] val underlying =
    dispatch.Http.configure {
      builder =>
        for (userAgent <- userAgent)
          builder.setUserAgent(userAgent)
        builder
    }
  def disposeInternal() = underlying.shutdown
  def handler[A](handler: Handler[A]): Request => Future[A] =
    x => handler.underlying match {
      case Left(handler) => underlying(x.underlying > handler)
      case Right(handler) => underlying(x.underlying > handler)
    }
}
object Client {
  private[this] implicit def asyncHandler2handler[A](asyncHandler: ahc.AsyncHandler[A]) = new Handler[A] { def underlying = Right(asyncHandler) }
  private[this] implicit def function2handler[A](f: ahc.Response => A) = new Handler[A] { def underlying = Left(f) }
  val string: Handler[String] = dispatch.as.String
  def file(x: java.io.File): Handler[_] = asyncHandler2handler(dispatch.as.File(x))
}