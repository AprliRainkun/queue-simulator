package style95.generator

import akka.actor.{Actor, ActorRef, Terminated, Props, PoisonPill}
import style95.Container.ActivationMessage

object AverageTpsObserver {
  final case class AverageTps(tps: Double, secs: Double)

  def props(monitor: ActorRef): Props = Props(new AverageTpsObserver(monitor))
}

class AverageTpsObserver(monitor: ActorRef) extends Actor {
  import AverageTpsObserver._

  private var hits = 0

  override def receive: Receive = {
    case ActivationMessage =>
      context.become(started(sender, System.nanoTime()))
  }

  private def started(requester: ActorRef, startTime: Long): Receive = {
    case ActivationMessage =>
      hits += 1
    case Terminated(`requester`) =>
      val secs = (System.nanoTime() - startTime).toDouble / 1e9
      monitor ! AverageTps(hits, secs)
      self ! PoisonPill
  }
}
