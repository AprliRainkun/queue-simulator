package style95.generator

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.{log, random}

case class Maintain(tps: Int) extends BehaviorDescriptor with Stationary {
  class Gen extends TimingGenerator {
    def next(elapsed: FiniteDuration): FiniteDuration = {
      require(tps >= 1, "tps should be at least one")
      // avoid log(0)
      (-log(1.0 - random()) / tps) seconds
    }
  }

  def timing: TimingGenerator = new Gen
}
