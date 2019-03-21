package style95.generator

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.{log, random}

case class Transition(startTps: Int, endTps: Int) {
  def in(duration: FiniteDuration): CompleteTransition =
    new CompleteTransition(startTps, endTps, duration)
}

class CompleteTransition private[generator](startTps: Int,
                                            endTps: Int,
                                            val duration: FiniteDuration)
    extends IntervalBehavior {
  class Behavior extends BehaviorDescriptor {
    class Gen extends TimingGenerator {
      def next(elapsed: FiniteDuration): FiniteDuration = {
        require(startTps >= 0 && endTps >= 0, "tps should be positive")

        val tps = lerp(startTps, endTps, elapsed / duration)
        // avoid log(0)
        // avoid zero division
        (-log(1.0 - random()) / (tps + 1e-6)) seconds
      }
      private def lerp(start: Double, end: Double, t: Double) =
        t * (end - start) + start

    }
    def timing: TimingGenerator = new Gen
  }

  def behavior: BehaviorDescriptor = new Behavior
}
