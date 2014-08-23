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

import GabblerService.Message
import akka.actor.{ Actor, FSM, Props }
import scala.concurrent.duration.FiniteDuration
import spray.http.StatusCode
import akka.persistence.PersistentActor
import spray.http.StatusCodes
import akka.actor.ActorRef
import akka.actor.Terminated

object Gabbler {
  case class Post(message: Message, completer: StatusCode => Unit)

  case class Listen(ref: ActorRef)
  case object Update

  def props(logId: String) = Props(new Gabbler(logId))
}

class Gabbler(logId: String) extends PersistentActor {
  import Gabbler._

  override def persistenceId = logId

  /*
   * Replayers can listen for immediate notification upon updates.
   */
  var listeners = Set.empty[ActorRef]

  def receiveRecover = Actor.emptyBehavior

  def receiveCommand = {
    case Post(msg, completer) =>
      persistAsync(msg) { _ =>
        completer(StatusCodes.NoContent)
        listeners foreach (_ ! Update)
      }
    case Listen(ref)     => listeners += context.watch(ref)
    case Terminated(ref) => listeners -= ref
  }
}