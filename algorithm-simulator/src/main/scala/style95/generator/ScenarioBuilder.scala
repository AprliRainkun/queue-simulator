package style95.generator

import scala.concurrent.duration._

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
}

trait IntervalBehavior extends BehaviorActorBuilder {
  def behavior: BehaviorDescriptor
  def duration: FiniteDuration

  def against(recv: ActorRef): Props = SingleBehaviorActor.props(this, recv)
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

  def against(recv: ActorRef): Props =
    SequentialBehaviorActor.props(sequence.reverse, recv)
}
