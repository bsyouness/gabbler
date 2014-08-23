package de.heikoseeberger.gabbler

import akka.persistence.PersistentView
import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.Cancellable
import scala.concurrent.duration.FiniteDuration
import GabblerService.{ Message, Cursor }
import spray.json.DefaultJsonProtocol
import akka.actor.Props
import akka.actor.Actor
import akka.event.LoggingReceive
import akka.persistence.SnapshotOffer

object Replayer {

  /*
   * Message from the web client.
   */
  case class GetSince(id: Long)

  /*
   * Reply to the web client (with JSON serialization).
   */
  case class Messages(firstId: Long, lastId: Long, list: Seq[Message])
  object Messages extends DefaultJsonProtocol {
    implicit val format = jsonFormat3(apply)
  }

  /*
   * Management message to request passivation after idle timeout.
   */
  case class PassivateRequest(cursor: Cursor, id: Long)
  /*
   * Management response: yes, please do passivate!
   */
  case object Passivate

  /*
   * Again, good style to create Props in the Actor’s companion object.
   */
  def props(logId: String, name: Cursor, gabbler: ActorRef, manager: ActorRef,
    inactivityTimeout: FiniteDuration, requestTimeout: FiniteDuration) =
    Props(new Replayer(logId, name, gabbler, manager, inactivityTimeout, requestTimeout))
}

class Replayer(logId: String, name: Cursor, gabbler: ActorRef, manager: ActorRef,
  inactivityTimeout: FiniteDuration, requestTimeout: FiniteDuration)
    extends PersistentView with ActorLogging {

  import Gabbler._
  import Replayer._

  /*
   * which events to replay?
   */
  override def persistenceId = logId
  /*
   * where to store confirmations (i.e. snapshots)?
   */
  override def viewId = logId + name.id

  /*
   * register for immediate update notifications
   */
  gabbler ! Listen(self)

  override def unhandled(message: Any) = message match {
    case Update    => self ! akka.persistence.Update(await = true)
    case Passivate => context.stop(self)
    case msg       => log.info("received unknown message {} in state {}", msg, this)
  }

  def receive = Actor.emptyBehavior

  override def preStart(): Unit = {
    initial() // get into the initial behavior
    super.preStart() // this will initiate the replay from the Journal
  }

  def initial(): Unit = {
    setTimeout(inactivityTimeout)
    context.become(LoggingReceive {
      case SnapshotOffer(meta, confirmed: Long) => this.confirmed = confirmed
      case m: Message if isPersistent && lastSequenceNr > confirmed =>
        msgPending((lastSequenceNr, m) :: Nil)
      case m: Message => // ignore old message
      case GetSince(id) =>
        confirmUpTo(id)
        retrievalPending(sender())
      case Timeout(id) =>
        uponTimeout(id)(manager ! PassivateRequest(name, confirmed))
    })
  }

  def msgPending(messages: List[(Long, Message)]): Unit =
    context.become(LoggingReceive {
      case m: Message if isPersistent && lastSequenceNr > confirmed =>
        msgPending((lastSequenceNr, m) :: messages)
      case m: Message => // ignore old message
      case GetSince(id) =>
        confirmUpTo(id)
        sender() ! mkMessages(messages filter (_._1 > confirmed))
        initial()
      case Timeout(id) =>
        uponTimeout(id)(manager ! PassivateRequest(name, confirmed))
    })

  def retrievalPending(client: ActorRef): Unit = {
    setTimeout(requestTimeout)
    context.become(LoggingReceive {
      case m: Message if isPersistent && lastSequenceNr > confirmed =>
        client ! mkMessages((lastSequenceNr, m) :: Nil)
        initial()
      case m: Message => // ignore old message
      case GetSince(id) =>
        client ! mkMessages(Nil)
        retrievalPending(sender())
      case Timeout(id) =>
        uponTimeout(id) {
          client ! mkMessages(Nil)
          initial()
        }
    })
  }

  def mkMessages(list: List[(Long, Message)]) = Messages(confirmed, lastSequenceNr, list.unzip._2)

  /*
   * Snapshot (i.e. confirmation) handling
   */
  var confirmed = 0L
  override def snapshotSequenceNr = confirmed
  def confirmUpTo(id: Long): Unit =
    if (id > confirmed) {
      confirmed = id
      saveSnapshot(id)
    }

  /*
   * Timeout handling
   */
  val timeoutIds = Iterator from 1
  var currentTimeout = 0
  var currentTask = Option.empty[Cancellable]

  case class Timeout(id: Int)

  def setTimeout(t: FiniteDuration): Unit = {
    val timeout = Timeout(timeoutIds.next())
    import context.dispatcher
    currentTask foreach (_.cancel())
    currentTask = Some(context.system.scheduler.scheduleOnce(t, self, timeout))
    currentTimeout = timeout.id
  }

  def clearTimeout(): Unit = {
    currentTask foreach (_.cancel())
    currentTask = None
  }

  def uponTimeout(id: Int)(thunk: => Unit): Unit =
    if (currentTimeout == id) {
      currentTask = None
      currentTimeout = 0
      thunk
    }

  override def toString: String =
    s"task=$currentTask timeout=$currentTimeout confirmed=$confirmed lastId=$lastSequenceNr"
}
