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
import akka.actor.{ Actor, Props }
import scala.concurrent.duration.FiniteDuration

object Gabbler {

  type Completer = Seq[Message] => Unit

  def props(timeoutDuration: FiniteDuration): Props =
    Props(new Gabbler(timeoutDuration))
}

class Gabbler(timeoutDuration: FiniteDuration) extends Actor {

  import Gabbler._

  def receive: Receive =
    waiting

  def waiting: Receive = {
    case completer: Completer => context become waitingForMessage(completer)
    case message: Message     => context become waitingForCompleter(message +: Nil)
  }

  def waitingForMessage(completer: Completer): Receive = {
    case completer: Completer => waitingForMessage(completer)
    case message: Message     => completeAndWait(completer, message +: Nil)
  }

  def waitingForCompleter(messages: Seq[Message]): Receive = {
    case completer: Completer => completeAndWait(completer, messages)
    case message: Message     => waitingForCompleter(message +: messages)
  }

  def completeAndWait(completer: Completer, messages: Seq[Message]): Unit = {
    completer(messages)
    context become waiting
  }
}
