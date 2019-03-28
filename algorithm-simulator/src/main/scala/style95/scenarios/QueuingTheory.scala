package style95.scenarios

import akka.actor.ActorRef
import style95.Container.ContainerProperty
import style95.generator._
import style95.scaler._

import scala.concurrent.duration._
import scala.language.postfixOps

// A test with queuing theory. Spawn all container at once and make
// TPS fixed.
object QueuingTheory extends ScenarioBase {
  override def actorBuilder: BehaviorActorBuilder =
    ScenarioBuilder startsWith {
      DoNothing in 3.seconds
    } next {
      Maintain(7) in 30.seconds
    }

  override def buildScaler(creatingHint: FiniteDuration,
                           consultInterval: FiniteDuration,
                           logger: ActorRef) = new SpawnAtOnce(1)

  override def containerProperty = ContainerProperty(300 millis, 100 millis)
}
