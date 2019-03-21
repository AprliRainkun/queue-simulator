package style95.generator

import scala.concurrent.duration._
import scala.language.implicitConversions

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

trait IntervalBehavior {
  def behavior: BehaviorDescriptor
  def duration: FiniteDuration

  def against(recv: ActorRef): Props = SingleBehaviorActor.props(this, recv)
}

case class WrappedIntervalBehavior(behavior: BehaviorDescriptor,
                                   duration: FiniteDuration)
    extends IntervalBehavior

class ScenarioBuilder(val sequence: List[IntervalBehavior]) {

  def next(behavior: IntervalBehavior): ScenarioBuilder = {
    val newSeq = behavior :: sequence
    new ScenarioBuilder(newSeq)
  }

  def toActor: Props = ???

  implicit def behaviorToBuilder(behavior: IntervalBehavior): ScenarioBuilder =
    new ScenarioBuilder(List(behavior))
}
