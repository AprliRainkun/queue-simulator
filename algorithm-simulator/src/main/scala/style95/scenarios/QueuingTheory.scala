package style95.scenarios

import style95.generator._
import style95.scaler._

import scala.concurrent.duration._

object QueuingTheory extends ScenarioBase {
  override def actorBuilder: BehaviorActorBuilder =
    ScenarioBuilder startsWith {
      DoNothing in 3.seconds
    } next {
      Maintain(7) in 30.seconds
    }

  override def scaler: Scaler = new SpawnAtOnce(1)
}
