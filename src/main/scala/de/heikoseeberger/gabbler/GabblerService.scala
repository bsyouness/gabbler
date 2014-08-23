/*
 * Copyright 2014 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.gabbler

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.io.IO
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import spray.can.Http
import spray.http.{ StatusCode, StatusCodes }
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.{ HttpServiceActor, Route }
import spray.routing.authentication.BasicAuth
import scala.concurrent.forkjoin.ThreadLocalRandom
import akka.actor.Terminated
import spray.http.HttpResponse
import spray.http.HttpEntity
import spray.http.HttpHeaders.Location
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.SupervisorStrategy

object GabblerService {

  /*
   * The main (and only) custom data type exchanged with the client
   */
  case class Message(username: String, text: String)

  object Message extends DefaultJsonProtocol {
    implicit val format = jsonFormat2(apply)
  }

  /*
   * It is good style to create an Actor’s Props in its companion object.
   */
  def props(hostname: String, port: Int, timeout: FiniteDuration): Props =
    Props(new GabblerService(hostname, port, timeout))

  /*
   * This is a helper class for generating unique Cursor IDs.
   */
  case class Cursor(id: String) {
    require(id forall (Cursor.allowed.indexOf(_) != -1))
  }
  object Cursor {
    private val allowed = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val template = "xxxxxxxxxx"
    private def randomChar(x: Char) = allowed.charAt(ThreadLocalRandom.current().nextInt(allowed.length))
    def apply(): Cursor = Cursor(template map randomChar)

  }

  /*
   * Internal message to track when GET requests finish
   */
  private case class Finished(c: Cursor, since: Long)

  /*
   * Using JSON serialization for the Journal as well
   */
  class Serializer extends akka.serialization.Serializer {
    override def identifier = 4243
    override def includeManifest = false
    override def toBinary(obj: AnyRef): Array[Byte] = obj match {
      case m: Message => m.toJson.compactPrint.getBytes("UTF-8")
      case _          => throw new IllegalArgumentException("unknown type " + obj.getClass)
    }
    override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
      new String(bytes, "UTF-8").parseJson.convertTo[Message]
  }
}

class GabblerService(hostname: String, port: Int, timeout: FiniteDuration) extends HttpServiceActor
    with ActorLogging with SprayJsonSupport {

  import GabblerService._
  import Replayer._
  import context.dispatcher

  IO(Http)(context.system) ! Http.Bind(self, hostname, port) // For details see my blog post http://goo.gl/XwOv7P

  /*
   * When Gabber dies, this service does shut down as well.
   */
  val gabbler = context.watch(context.actorOf(Gabbler.props("room-gabbler"), "room-gabbler"))

  /*
   * Failures considered fatal in this demo.
   */
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  def receive =
    runRoute(apiRoute ~ staticRoute) orElse {
      case Finished(cursor, since) =>
        if (openRequests get cursor exists (since ==))
          openRequests -= cursor
      case PassivateRequest(cursor, since) =>
        openRequests get cursor match {
          case Some(id) if id != since => // ignore
          case _ =>
            sender() ! Passivate
            openRequests -= cursor
            cursors -= cursor
        }
    }

  def apiRoute: Route =
    authenticate(BasicAuth(UsernameEqualsPasswordAuthenticator, "Gabbler")) { user =>
      pathPrefix("api") {
        pathPrefix("cursors") { // The Read-Side
          pathEnd {
            post { ctx => // request a new persistent cursor
              val c = Cursor()
              getCursor(c)
              ctx.complete(HttpResponse(
                status = StatusCodes.Created,
                headers = Location("/api/cursors/" + c.id) :: Nil,
                entity = HttpEntity.Empty))
            }
          } ~
            path(Segment) { id => // use a persistent cursor
              get {
                parameter("since".as[Long]) { since =>
                  implicit val t = Timeout(10.seconds)
                  val c = Cursor(id)
                  openRequests += c -> since
                  val f = getCursor(c) ? GetSince(since)
                  f.onSuccess {
                    case _ => self ! Finished(c, since)
                  }
                  complete(f.mapTo[Messages])
                }
              }
            }
        } ~
          path("messages") { // The Write-Side
            post {
              entity(as[Message]) { message =>
                produce(instanceOf[StatusCode]) { completer =>
                  _ =>
                    log.debug("User '{}' has posted '{}'", user.username, message.text)
                    gabbler ! Gabbler.Post(message.copy(username = user.username), completer)
                }
              }
            }
          } ~
          path("shutdown") {
            get {
              complete {
                val system = context.system
                system.scheduler.scheduleOnce(1 second)(system.shutdown())
                "Shutting down in 1 second ..."
              }
            }
          } ~
          path(Segments) { xs =>
            ctx =>
              log.warning("unmatched paths {} with context {}", xs, ctx)
              ctx.complete(StatusCodes.NotFound)
          }
      }
    }

  /*
   * This route serves all static content (i.e. HTML, CSS, JS).
   */
  def staticRoute: Route =
    path("") {
      getFromResource("web/index.html")
    } ~ getFromResourceDirectory("web")

  /*
   * Tracking currently outstanding GET requests in order to properly react to Passivate requests.
   */
  private var openRequests = Map.empty[Cursor, Long]

  /*
   * Creating a new cursor means creating a Replayer actor, which will need a fresh name as well.
   */
  private var nameCounter = Iterator from 1
  private var cursors = Map.empty[Cursor, ActorRef]

  /*
   * Get or create the cursor’s Actor, which may rehydrate from the Journal when necessary.
   */
  def getCursor(c: Cursor): ActorRef =
    cursors get c getOrElse {
      val replayer = context.actorOf(
        Replayer.props("room-gabbler", c, gabbler, self, 30.seconds, 5.seconds),
        s"cursor-${c.id}-${nameCounter.next()}")
      cursors += c -> replayer
      replayer
    }
}
