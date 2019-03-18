package style95.generator

import scala.concurrent.duration.FiniteDuration

case object DoNothing extends BehaviorDescriptor with Stationary {
  class Gen extends TimingGenerator {
    def next(elapsed: FiniteDuration): Option[FiniteDuration] = None
  }
  def timing: TimingGenerator = new Gen
}
