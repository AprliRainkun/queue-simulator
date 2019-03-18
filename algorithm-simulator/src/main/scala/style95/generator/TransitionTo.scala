package style95.generator

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.{log, random}

case class TransitionTo(startTps: Int, endTps: Int) {
  def in(duration: FiniteDuration): CompleteTransitionTo =
    new CompleteTransitionTo(startTps, endTps, duration)
}

class CompleteTransitionTo private[generator] (startTps: Int,
                                               endTps: Int,
                                               val duration: FiniteDuration)
    extends IntervalBehavior {
  class Behavior extends BehaviorDescriptor {
    class Gen extends TimingGenerator {
      def next(elapsed: FiniteDuration): Option[FiniteDuration] = {
        require(startTps >= 1, "startTps should be at least one")
        require(endTps >= 1, "endTps should be at least one")

        val tps = lerp(startTps, endTps, elapsed / duration)
        val delay = (-log(random()) / tps) seconds

        Some(delay)
      }
      private def lerp(start: Double, end: Double, t: Double) =
        t * (end - start) + start

    }
    def timing: TimingGenerator = new Gen
  }

  def behavior: BehaviorDescriptor = new Behavior
}
