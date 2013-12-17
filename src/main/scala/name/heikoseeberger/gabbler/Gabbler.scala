/*
 * Copyright 2013 Heiko Seeberger
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

package name.heikoseeberger.gabbler

import GabblerService.Message
import akka.actor.{ Actor, Props }
import scala.concurrent.duration.FiniteDuration

object Gabbler {

  type Completer = Seq[Message] => Unit

  case class Timeout(id: Int)

  def props(timeoutDuration: FiniteDuration): Props =
    Props(new Gabbler(timeoutDuration))
}

class Gabbler(timeoutDuration: FiniteDuration) extends Actor {

  import Gabbler._
  import context.dispatcher

  def receive: Receive =
    waiting(scheduleTimeout(Timeout(0)))

  def waiting(implicit timeout: Timeout): Receive = {
    case completer: Completer => context become waitingForMessage(completer)(newTimeout(timeout))
    case message: Message     => context become waitingForCompleter(message +: Nil)
    case `timeout`            => context.stop(self)
  }

  def waitingForMessage(completer: Completer)(implicit timeout: Timeout): Receive = {
    case completer: Completer => waitingForMessage(completer)(newTimeout(timeout))
    case message: Message     => completeAndWait(completer, message +: Nil)
    case `timeout`            => completeAndWait(completer, Nil)
  }

  def waitingForCompleter(messages: Seq[Message])(implicit timeout: Timeout): Receive = {
    case completer: Completer => completeAndWait(completer, messages)
    case message: Message     => waitingForCompleter(message +: messages)
    case `timeout`            => context.stop(self)
  }

  def completeAndWait(completer: Completer, messages: Seq[Message])(implicit timeout: Timeout): Unit = {
    completer(messages)
    context become waiting(newTimeout(timeout))
  }

  def newTimeout(timeout: Timeout): Timeout =
    scheduleTimeout(timeout.copy(timeout.id + 1))

  def scheduleTimeout(timeout: Timeout): Timeout = {
    context.system.scheduler.scheduleOnce(timeoutDuration, self, timeout)
    timeout
  }
}
