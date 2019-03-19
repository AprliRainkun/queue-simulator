package style95.generator

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import style95.Container.ActivationMessage
import style95.generator.SingleBehaviorActor.{SendRequest, Terminate}

import scala.concurrent.duration._
import scala.language.postfixOps

object SingleBehaviorActor {
  def props(behavior: IntervalBehavior)(receiver: ActorRef): Props =
    Props(new SingleBehaviorActor(behavior, receiver))

  final case object SendRequest
  final case object Terminate
}

class SingleBehaviorActor(behavior: IntervalBehavior, receiver: ActorRef)
    extends Actor
    with ActorLogging {
  import context.{dispatcher, system}

  private var timingGen: TimingGenerator = _
  private var start: Long = _

  override def preStart(): Unit = {
    timingGen = behavior.behavior.timing
    start = System.nanoTime()

    system.scheduler.scheduleOnce(behavior.duration, self, Terminate)
    scheduleNext()
  }

  override def receive: Receive = {
    case SendRequest =>
      receiver ! ActivationMessage(self, System.nanoTime())
      scheduleNext()
    case Terminate => self ! PoisonPill
  }

  private def scheduleNext() = {
    timingGen.next((System.nanoTime() - start) nanos) match {
      case Some(delay) =>
        system.scheduler.scheduleOnce(delay, self, SendRequest)
      case _ =>
    }
  }
}

object SequentialBehaviorActor {

}
