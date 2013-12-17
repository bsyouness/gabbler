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

object Gabbler {

  type Completer = Seq[Message] => Unit

  def props: Props =
    Props(new Gabbler)
}

class Gabbler extends Actor {

  import Gabbler._

  var messages = List.empty[Message]

  var storedCompleter = Option.empty[Completer]

  def receive: Receive = {
    case completer: Completer =>
      if (messages.nonEmpty) {
        completer(messages)
        messages = Nil
      } else
        storedCompleter = Some(completer)
    case message: Message =>
      messages +:= message
      for (completer <- storedCompleter) {
        completer(messages)
        messages = Nil
        storedCompleter = None
      }
  }
}
