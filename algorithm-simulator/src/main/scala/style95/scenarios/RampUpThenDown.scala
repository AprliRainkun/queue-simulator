package style95.scenarios

import style95.generator._
import scala.concurrent.duration._

object RampUpThenDown extends ScenarioBase {
  override def actorBuilder: BehaviorActorBuilder =
    ScenarioBuilder startsWith {
      DoNothing in 5.seconds
    } next {
      Transition(0, 100) in 5.seconds
    } next {
      Maintain(100) in 10.seconds
    } next {
      Transition(100, 0) in 5.seconds
    } next {
      DoNothing in 5.seconds
    }
}