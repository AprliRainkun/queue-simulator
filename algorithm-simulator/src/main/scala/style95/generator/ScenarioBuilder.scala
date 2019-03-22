package style95.generator

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorRef, Props}

trait BehaviorDescriptor {
  def timing: TimingGenerator
}

trait Stationary { this: BehaviorDescriptor =>
  def in(duration: FiniteDuration): IntervalBehavior =
    WrappedIntervalBehavior(this, duration)

}

trait TimingGenerator {
  def next(elapsed: FiniteDuration): FiniteDuration
}

trait BehaviorActorBuilder {
  def against(recv: ActorRef): Props
  def runningDuration: FiniteDuration
}

trait IntervalBehavior extends BehaviorActorBuilder {
  def behavior: BehaviorDescriptor
  def duration: FiniteDuration

  override def against(recv: ActorRef): Props =
    SingleBehaviorActor.props(this, recv)

  override def runningDuration: FiniteDuration = duration
}

case class WrappedIntervalBehavior(behavior: BehaviorDescriptor,
                                   duration: FiniteDuration)
    extends IntervalBehavior

object ScenarioBuilder {
  def startsWith(behavior: IntervalBehavior) =
    new ScenarioBuilder(List(behavior))
}

class ScenarioBuilder(val sequence: List[IntervalBehavior])
    extends BehaviorActorBuilder {

  def next(behavior: IntervalBehavior): ScenarioBuilder = {
    val newSeq = behavior :: sequence
    new ScenarioBuilder(newSeq)
  }

  override def against(recv: ActorRef): Props =
    SequentialBehaviorActor.props(sequence.reverse, recv)

  override def runningDuration: FiniteDuration =
    sequence.map(_.duration).foldLeft(0 second)(_ + _)
}
